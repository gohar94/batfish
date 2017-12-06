package batfish.representation;

import java.io.Serializable;
import java.util.List;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class PolicyMap implements Serializable {

   private static final long serialVersionUID = 1L;

   private List<PolicyMapClause> _clauses;
   private String _mapName;

   public PolicyMap(String name, List<PolicyMapClause> clauses) {
      _mapName = name;
      _clauses = clauses;
   }

   public List<PolicyMapClause> getClauses() {
      return _clauses;
   }

   public String getMapName() {
      return _mapName;
   }

   public JSONObject getJSON() {
      JSONObject jsonObject = new JSONObject();
      jsonObject.put("name", _mapName);
      
      return jsonObject;
   }

}
