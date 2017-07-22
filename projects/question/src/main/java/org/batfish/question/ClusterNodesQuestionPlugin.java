package org.batfish.question;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.batfish.common.Answerer;
import org.batfish.common.Pair;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.collections.NamedStructureEquivalenceSet;
import org.batfish.datamodel.collections.NamedStructureEquivalenceSets;
import org.batfish.datamodel.questions.Question;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ClusterNodesQuestionPlugin extends QuestionPlugin {

   public static class ClusterNodesAnswerElement implements AnswerElement {
      
      private List<Set<String>> _clusters;

      private final String CLUSTERS_VAR = "clusters";

      public ClusterNodesAnswerElement() {
      }

      @JsonProperty(CLUSTERS_VAR)
      public List<Set<String>> getClusters() {
         return _clusters;
      }

      @Override
      public String prettyPrint() {
         StringBuilder sb = new StringBuilder(
               "Results for cluster nodes\n");         
         for(Set<String> cluster : _clusters) {
            sb.append(cluster.toString() + "\n");
         }
         return sb.toString();
      }

      @JsonProperty(CLUSTERS_VAR)
      public void setClusters(List<Set<String>> clusters) {
         _clusters = clusters;
      }
   }

   public static class ClusterNodesAnswerer extends Answerer {

      private ClusterNodesAnswerElement _answerElement;
      
      // the names of all nodes being analyzed
      private List<String> _nodes;

      // the minimum size of a name set for it to be included in the clustering analysis
      private static double THRESHOLD;
      
      public ClusterNodesAnswerer(Question question, IBatfish batfish) {
         super(question, batfish);
      }

      @Override
      public ClusterNodesAnswerElement answer() {

         ClusterNodesQuestion question = (ClusterNodesQuestion) _question;
         _answerElement = new ClusterNodesAnswerElement();
         
         // first get the results of compareSameName
         CompareSameNameQuestionPlugin.CompareSameNameQuestion inner = 
               new CompareSameNameQuestionPlugin.CompareSameNameQuestion();
         inner.setNodeRegex(question.getNodeRegex());
         inner.setNamedStructTypes(question.getNamedStructTypes());
         inner.setSingletons(true);
         CompareSameNameQuestionPlugin.CompareSameNameAnswerer innerAnswerer = 
               new CompareSameNameQuestionPlugin().createAnswerer(inner, _batfish);
         CompareSameNameQuestionPlugin.CompareSameNameAnswerElement innerAnswer = 
               innerAnswerer.answer();
         
         SortedMap<String, NamedStructureEquivalenceSets<?>> equivalenceSets = 
               innerAnswer.getEquivalenceSets();
         _nodes = innerAnswer.getNodes();
         
         // TODO: Figure out the appropriate threshold, if any
         THRESHOLD = 0.5 * _nodes.size() / question.getNumClusters();
         
         // use the CompareSameName info to create a data vector for each node
         Map<String,Set<String>> dataVectors = createDataVectors(equivalenceSets);

         // now do k-modes clustering on this data
         List<Set<String>> clusterSets = kModes(question.getNumClusters(), dataVectors.keySet());
         
         List<Set<String>> clusters = new ArrayList<>(clusterSets.size());
         for(Set<String> clusterSet : clusterSets) {
            Set<String> cluster = new TreeSet<>();
            for (String vector : clusterSet) {
               cluster.addAll(dataVectors.get(vector));
            }
            clusters.add(cluster);
         }
         _answerElement.setClusters(clusters);
         
         return _answerElement;
      }
      

      private <T> void addToDataVectors(NamedStructureEquivalenceSets<T> eSets, 
            Map<String, StringBuilder> vectors) {
         
         for (Set<NamedStructureEquivalenceSet<T>> eSet : eSets.getSameNamedStructures().values()) {
            // make sure the number of nodes with this name is large enough
            int count = 0;
            for(NamedStructureEquivalenceSet<T> eClass : eSet) {
               count += eClass.getNodes().size();
            }
            if (count < THRESHOLD) {
               continue;
            }
            for(StringBuilder sb : vectors.values()) {
               sb.append('0');
            }
            char className = 'a';
            for (NamedStructureEquivalenceSet<T> eClass : eSet) {
               for (String node : eClass.getNodes()) {
                  StringBuilder sb = vectors.get(node);
                  // TODO: Should we partition among equivalence classes, or just care about
                  // whether the name exists or not?  
//                  sb.setCharAt(sb.length() - 1, '1');
                  sb.setCharAt(sb.length() - 1, className);
               }
               className++;
            }
         }
      }
      
      private Map<String,Set<String>> createDataVectors(SortedMap<String, NamedStructureEquivalenceSets<?>> equivalenceSets) {
              
         Map<String, StringBuilder> vectors = new TreeMap<>();
         for(String node : _nodes) {
            vectors.put(node, new StringBuilder());
         }
         for (NamedStructureEquivalenceSets<?> eSets : equivalenceSets.values()) {
            addToDataVectors(eSets, vectors);
         }
         
         // invert vecs to produce a mapping from vectors to the nodes that have that vector
         Map<String,Set<String>> invertedVectors = new TreeMap<>();
         for (Map.Entry<String,StringBuilder> entry : vectors.entrySet()) {
            String vector = entry.getValue().toString();
            Set<String> nodes = invertedVectors.get(vector);
            if (nodes != null) {
               nodes.add(entry.getKey());
            }
            else {
               nodes = new TreeSet<String>();
               nodes.add(entry.getKey());
               invertedVectors.put(vector, nodes);
            }
         }
         return invertedVectors;
      }
      
      private int hammingDistance(String s1, String s2) {
         // we assume the strings have the same length
         int dist = 0;
         for(int i = 0; i < s1.length(); i++) {
            if (s1.charAt(i) != s2.charAt(i)) {
               dist++;
            }
         }
         return dist;
      }
      
      // find the element in choice[0..(i-1)] with the minimum hamming distance to s
      // return a pair of the index and hamming distance
      private Pair<Integer,Integer> minHammingDistance(String s, String[] choices, int len) {
         int min = Integer.MAX_VALUE;
         int minIndex = -1;
         for(int i = 0; i < len; i++) {
            int dist = hammingDistance(s, choices[i]);
            if(dist < min) {
               min = dist;
               minIndex = i;
            }
         }
         return new Pair<Integer,Integer>(minIndex, min);
      }
      
      // produce a new vector whose ith element is the mode of the ith elements of the given vectors
      private String elementwiseMode(Set<String> vectors, int strLen) {
         StringBuilder sb = new StringBuilder();
         for(int i = 0; i < strLen; i++) {
            char mode = 'a';
            int modeCount = 0;
            for(String vec : vectors) {
               char c = vec.charAt(i);
               int count = 0;
               for(String vec2 : vectors) {
                  if(c == vec2.charAt(i)) {
                     count++;
                  }
               }
               if (count > modeCount) {
                  modeCount = count;
                  mode = c;
               }
            }
            sb.append(mode);
         }
         return sb.toString();
      }
      
      // standard k-modes clustering, with a variant of the seeding technique from the k-means++ algorithm
      private List<Set<String>> kModes(int k, Set<String> vectors) {
         
         int vecLen = 0;
         for(String vector : vectors) {
            vecLen = vector.length();
            break;
         }
         
         String[] centers = new String[k];
         List<Set<String>> clusters = null;
         
         Random rand = new Random();         
         String[] vecArray = vectors.toArray(new String[vectors.size()]);
         int numVecs = vecArray.length;
         
         // use a variant of the k-means++ seeding algorithm:
         // choose the first center randomly
         centers[0] = vecArray[rand.nextInt(numVecs)];

         for (int c = 1; c < k; c++) {
            // for each vector, find its minimal distance to any already-chosen center, and choose the vector with the maximal such distance
            int max = -1;
            int index = -1;
            for(int i = 0; i < numVecs; i++) {
               int minDist = minHammingDistance(vecArray[i], centers, c).getSecond();
               if(minDist > max) {
                  max = minDist;
                  index = i;
               }
            }
            centers[c] = vecArray[index];            
         }
         
         boolean done = false;
         
         while(!done) {
         
            // compute the cluster of each vector: 
            // the cluster whose center has the minimum hamming distance to the vector
            clusters = new ArrayList<>(k);
            for(int i = 0; i < k; i++) {
               clusters.add(new TreeSet<>());
            }
            for (String vector : vectors) {
               int cluster = minHammingDistance(vector, centers, centers.length).getFirst();
               Set<String> clusterSet = clusters.get(cluster);
               clusterSet.add(vector);
            }
         
            // compute a new center for each cluster:
            // the vector containing the mode value for each element
            String[] newCenters = new String[k];
            int c = 0;
            for (Set<String> cluster : clusters) {
               newCenters[c] = elementwiseMode(cluster, vecLen);
               c++;
            }
            
            done = Arrays.equals(centers, newCenters);
            centers = newCenters;
         }
         return clusters;         
      }
   }

   // <question_page_comment>
   /**
    * Uses a form of clustering to partition nodes into equivalence classes.
    * <p>
    * Clusters nodes based on how similar their configurations are, based on the 
    * results of CompareSameName.
    *
    * @type ClusterNodes multifile
    *
    * @param namedStructTypes
    *           Set of structure types to analyze drawn from ( AsPathAccessList,
    *           CommunityList, IkeGateway, IkePolicies, IkeProposal,
    *           IpAccessList, IpsecPolicy, IpsecProposal, IpsecVpn,
    *           RouteFilterList, RoutingPolicy) Default value is '[]' (which
    *           denotes all structure types).
    * @param nodeRegex
    *           Regular expression for names of nodes to include. Default value
    *           is '.*' (all nodes).
    * @param numClusters
    *           The number of clusters to produce.  Default value is 5.
    *
    */
   public static final class ClusterNodesQuestion extends Question {

      private static final String NAMED_STRUCT_TYPES_VAR = "namedStructTypes";

      private static final String NODE_REGEX_VAR = "nodeRegex";
      
      private static final String NUM_CLUSTERS_VAR = "numClusters";

      private SortedSet<String> _namedStructTypes;

      private String _nodeRegex;
      
      private int _numClusters;

      public ClusterNodesQuestion() {
         _namedStructTypes = new TreeSet<>();
         _nodeRegex = ".*";
         _numClusters = 5;
      }

      @Override
      public boolean getDataPlane() {
         return false;
      }

      @Override
      public String getName() {
         return "ClusterNodes";
      }

      @JsonProperty(NAMED_STRUCT_TYPES_VAR)
      public SortedSet<String> getNamedStructTypes() {
         return _namedStructTypes;
      }

      @JsonProperty(NODE_REGEX_VAR)
      public String getNodeRegex() {
         return _nodeRegex;
      }
      
      @JsonProperty(NUM_CLUSTERS_VAR)
      public int getNumClusters() {
         return _numClusters;
      }

      @Override
      public boolean getTraffic() {
         return false;
      }

      @JsonProperty(NAMED_STRUCT_TYPES_VAR)
      public void setNamedStructTypes(SortedSet<String> namedStructTypes) {
         _namedStructTypes = namedStructTypes;
      }

      @JsonProperty(NODE_REGEX_VAR)
      public void setNodeRegex(String regex) {
         _nodeRegex = regex;
      }
      
      @JsonProperty(NUM_CLUSTERS_VAR)
      public void setNumClusters(int numClusters) {
         _numClusters = numClusters;
      }

   }

   @Override
   protected Answerer createAnswerer(Question question, IBatfish batfish) {
      return new ClusterNodesAnswerer(question, batfish);
   }

   @Override
   protected Question createQuestion() {
      return new ClusterNodesQuestion();
   }

}