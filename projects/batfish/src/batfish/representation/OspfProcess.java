package batfish.representation;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class OspfProcess implements Serializable {

   private static final long serialVersionUID = 1L;

   private Map<Long, OspfArea> _areas;
   private Set<GeneratedRoute> _generatedRoutes;
   private Set<PolicyMap> _outboundPolicyMaps;
   private Map<PolicyMap, OspfMetricType> _policyMetricTypes;
   private Double _referenceBandwidth;
   private String _routerId;

   public OspfProcess() {
      _generatedRoutes = new LinkedHashSet<GeneratedRoute>();
      _outboundPolicyMaps = new LinkedHashSet<PolicyMap>();
      _policyMetricTypes = new LinkedHashMap<PolicyMap, OspfMetricType>();
      _referenceBandwidth = null;
      _routerId = null;
      _areas = new HashMap<Long, OspfArea>();
   }

   public Map<Long, OspfArea> getAreas() {
      return _areas;
   }

   public Set<GeneratedRoute> getGeneratedRoutes() {
      return _generatedRoutes;
   }

   public Set<PolicyMap> getOutboundPolicyMaps() {
      return _outboundPolicyMaps;
   }

   public Map<PolicyMap, OspfMetricType> getPolicyMetricTypes() {
      return _policyMetricTypes;
   }

   public double getReferenceBandwidth() {
      return _referenceBandwidth;
   }

   public String getRouterId() {
      return _routerId;
   }

   public void setReferenceBandwidth(double referenceBandwidth) {
      _referenceBandwidth = referenceBandwidth;
   }

   public void setRouterId(String id) {
      _routerId = id;
   }

   public JSONObject getJSON() {
       JSONObject jsonObject = new JSONObject();
       jsonObject.put("routerId", _routerId);
       jsonObject.put("referenceBandwidth", _referenceBandwidth);
       Iterator<GeneratedRoute> itr = _generatedRoutes.iterator();
       JSONArray jGeneratedRoutes = new JSONArray();
       while (itr.hasNext()) {
           jGeneratedRoutes.add((Object)itr.next().getJSON());
       }
       jsonObject.put("generatedRoutes", jGeneratedRoutes);
       Iterator<PolicyMap> itr2 = _outboundPolicyMaps.iterator();
       JSONArray jOutboundPolicyMaps = new JSONArray();
       while (itr2.hasNext()) {
           jOutboundPolicyMaps.add((Object)itr2.next().getJSON());
       }
       jsonObject.put("outboundPolicyMaps", jOutboundPolicyMaps);
       return jsonObject;
   }

}
