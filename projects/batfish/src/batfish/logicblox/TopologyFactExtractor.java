package batfish.logicblox;

import java.io.FileWriter;
import java.util.Map;

import batfish.representation.Edge;
import batfish.representation.Topology;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class TopologyFactExtractor {

   private Topology _topology;

   public TopologyFactExtractor(Topology topology) {
      _topology = topology;
   }

   public void writeFacts(Map<String, StringBuilder> factBins) {
      StringBuilder wSamePhysicalSegment = factBins.get("SamePhysicalSegment");
      for (Edge e : _topology.getEdges()) {
         wSamePhysicalSegment.append(e.getNode1() + "|" + e.getInt1() + "|"
               + e.getNode2() + "|" + e.getInt2() + "\n");
      }
   }

   public void writeToJson() {
        JSONObject jsonObject = new JSONObject();
        JSONArray jEdges = new JSONArray();
        String filename = "topology.json";
        for (Edge e : _topology.getEdges()) {
            jEdges.add((Object)e.getJSON());
        }
        jsonObject.put("edges", jEdges);
	    try {
            FileWriter fileWriter = new FileWriter(filename);
            String jsonFormattedString = jsonObject.toJSONString();
		    fileWriter.write(jsonFormattedString);
		    fileWriter.flush();
        } catch (Exception e) {
		 e.printStackTrace();
	  }
	  System.out.println("Written JSON Object to file " + filename);
   }

}
