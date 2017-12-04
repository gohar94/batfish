package batfish.logicblox;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import batfish.main.BatfishException;
import batfish.representation.BgpNeighbor;
import batfish.representation.BgpProcess;
import batfish.representation.CommunityList;
import batfish.representation.CommunityListLine;
import batfish.representation.Configuration;
import batfish.representation.GeneratedRoute;
import batfish.representation.Interface;
import batfish.representation.Ip;
import batfish.representation.IpAccessList;
import batfish.representation.IpAccessListLine;
import batfish.representation.OriginType;
import batfish.representation.OspfArea;
import batfish.representation.OspfMetricType;
import batfish.representation.OspfProcess;
import batfish.representation.PolicyMap;
import batfish.representation.PolicyMapClause;
import batfish.representation.PolicyMapMatchCommunityListLine;
import batfish.representation.PolicyMapMatchIpAccessListLine;
import batfish.representation.PolicyMapMatchLine;
import batfish.representation.PolicyMapMatchNeighborLine;
import batfish.representation.PolicyMapMatchProtocolLine;
import batfish.representation.PolicyMapMatchRouteFilterListLine;
import batfish.representation.PolicyMapMatchTagLine;
import batfish.representation.PolicyMapSetAddCommunityLine;
import batfish.representation.PolicyMapSetCommunityLine;
import batfish.representation.PolicyMapSetDeleteCommunityLine;
import batfish.representation.PolicyMapSetLine;
import batfish.representation.PolicyMapSetLocalPreferenceLine;
import batfish.representation.PolicyMapSetMetricLine;
import batfish.representation.PolicyMapSetNextHopLine;
import batfish.representation.PolicyMapSetOriginTypeLine;
import batfish.representation.Protocol;
import batfish.representation.RouteFilterLengthRangeLine;
import batfish.representation.RouteFilterLine;
import batfish.representation.RouteFilterList;
import batfish.representation.StaticRoute;
import batfish.representation.SwitchportEncapsulationType;
import batfish.util.SubRange;
import batfish.util.Util;
import batfish.z3.Synthesizer;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class ConfigurationFactExtractor {

   private static final int DEFAULT_CISCO_VLAN_OSPF_COST = 1;

   private static final String FLOW_SINK_INTERFACE_PREFIX = "TenGigabitEthernet100/";

   private static String getLBRoutingProtocol(Protocol prot) {
      switch (prot) {
      case AGGREGATE:
         return "aggregate";
      case BGP:
         return "bgp";
      case CONNECTED:
         return "connected";
      case EGP:
         return "egp";
      case IBGP:
         return "ibgp";
      case IGP:
         return "igp";
      case OSPF:
         return "ospf";
      case OSPF_E1:
         return "ospfE1";
      case OSPF_E2:
         return "ospfE2";
      case STATIC:
         return "static";
      default:
         break;
      }
      return null;
   }

   private Set<Long> _allCommunities;
   private Configuration _configuration;
   private Map<String, StringBuilder> _factBins;
   private List<String> _warnings;

   public ConfigurationFactExtractor(Configuration c, Set<Long> allCommunities,
         Map<String, StringBuilder> factBins) {
      _configuration = c;
      _allCommunities = allCommunities;
      _factBins = factBins;
      _warnings = new ArrayList<String>();
   }

   public List<String> getWarnings() {
      return _warnings;
   }

   private void writeBgpGeneratedRoutes() {
      String hostname = _configuration.getHostname();
      BgpProcess proc = _configuration.getBgpProcess();
      StringBuilder wSetBgpGeneratedRoute_flat = _factBins
            .get("SetBgpGeneratedRoute_flat");
      StringBuilder wSetBgpGeneratedRoutePolicy_flat = _factBins
            .get("SetBgpGeneratedRoutePolicy_flat");
      if (proc != null) {
         for (GeneratedRoute gr : proc.getGeneratedRoutes()) {
            long network_start = gr.getPrefix().asLong();
            int prefix_length = gr.getPrefixLength();
            long network_end = Util.getNetworkEnd(network_start, prefix_length);
            wSetBgpGeneratedRoute_flat.append(hostname + "|" + network_start
                  + "|" + network_end + "|" + prefix_length + "\n");
            for (PolicyMap generationPolicy : gr.getGenerationPolicies()) {
               String gpName = hostname + ":" + generationPolicy.getMapName();
               wSetBgpGeneratedRoutePolicy_flat.append(hostname + "|"
                     + network_start + "|" + network_end + "|" + prefix_length
                     + "|" + gpName + "\n");
            }
         }
      }
   }

   private void writeBgpNeighborGeneratedRoutes() {
      String hostname = _configuration.getHostname();
      BgpProcess proc = _configuration.getBgpProcess();
      StringBuilder wSetBgpNeighborGeneratedRoute_flat = _factBins
            .get("SetBgpNeighborGeneratedRoute_flat");
      StringBuilder wSetBgpNeighborGeneratedRoutePolicy_flat = _factBins
            .get("SetBgpNeighborGeneratedRoutePolicy_flat");
      if (proc != null) {
         for (BgpNeighbor neighbor : proc.getNeighbors().values()) {
            Long neighborIp = neighbor.getAddress().asLong();
            for (GeneratedRoute gr : neighbor.getGeneratedRoutes()) {
               long network_start = gr.getPrefix().asLong();
               int prefix_length = gr.getPrefixLength();
               long network_end = Util.getNetworkEnd(network_start,
                     prefix_length);
               wSetBgpNeighborGeneratedRoute_flat.append(hostname + "|"
                     + neighborIp + "|" + network_start + "|" + network_end
                     + "|" + prefix_length + "\n");
               for (PolicyMap generationPolicy : gr.getGenerationPolicies()) {
                  String gpName = hostname + ":"
                        + generationPolicy.getMapName();
                  wSetBgpNeighborGeneratedRoutePolicy_flat.append(hostname
                        + "|" + neighborIp + "|" + network_start + "|"
                        + network_end + "|" + prefix_length + "|" + gpName
                        + "\n");
               }
            }
         }
      }
   }

   private void writeBgpNeighborPolicies() {
      StringBuilder wSetBgpImportPolicy = _factBins.get("SetBgpImportPolicy");
      StringBuilder wSetBgpExportPolicy = _factBins.get("SetBgpExportPolicy");
      String hostname = _configuration.getHostname();
      BgpProcess proc = _configuration.getBgpProcess();
      if (proc != null) {
         for (BgpNeighbor neighbor : proc.getNeighbors().values()) {
            Ip neighborAddress = neighbor.getAddress();
            for (PolicyMap inboundMap : neighbor.getInboundPolicyMaps()) {
               String inboundMapName = hostname + ":" + inboundMap.getMapName();
               wSetBgpImportPolicy.append(hostname + "|"
                     + neighborAddress.asLong() + "|" + inboundMapName + "\n");
            }
            for (PolicyMap outboundMap : neighbor.getOutboundPolicyMaps()) {
               String outboundMapName = hostname + ":"
                     + outboundMap.getMapName();
               wSetBgpExportPolicy.append(hostname + "|"
                     + neighborAddress.asLong() + "|" + outboundMapName + "\n");
            }
         }
      }
   }

   private void writeBgpNeighbors() {
      StringBuilder wSetBgpNeighborIp = _factBins.get("SetBgpNeighborIp");
      StringBuilder wSetLocalAs = _factBins.get("SetLocalAs");
      StringBuilder wSetRemoteAs = _factBins.get("SetRemoteAs");
      StringBuilder wSetBgpNeighborDefaultMetric = _factBins
            .get("SetBgpNeighborDefaultMetric");
      StringBuilder wSetBgpNeighborSendCommunity = _factBins
            .get("SetBgpNeighborSendCommunity");
      String hostname = _configuration.getHostname();
      BgpProcess proc = _configuration.getBgpProcess();
      if (proc != null) {
         for (BgpNeighbor neighbor : proc.getNeighbors().values()) {
            int remoteAs = neighbor.getRemoteAs();
            int localAs = neighbor.getLocalAs();
            int defaultMetric = neighbor.getDefaultMetric();
            Ip neighborIp = neighbor.getAddress();
            wSetBgpNeighborIp.append(hostname + "|" + neighborIp.asLong()
                  + "\n");
            wSetLocalAs.append(hostname + "|" + neighborIp.asLong() + "|"
                  + localAs + "\n");
            wSetRemoteAs.append(hostname + "|" + neighborIp.asLong() + "|"
                  + remoteAs + "\n");
            wSetBgpNeighborDefaultMetric.append(hostname + "|"
                  + neighborIp.asLong() + "|" + defaultMetric + "\n");
            if (neighbor.getSendCommunity()) {
               wSetBgpNeighborSendCommunity.append(hostname + "|"
                     + neighborIp.asLong() + "\n");
            }
         }
      }
   }

   private void writeCommunityLists() {
      StringBuilder wSetCommunityListLine = _factBins
            .get("SetCommunityListLine");
      StringBuilder wSetCommunityListLinePermit = _factBins
            .get("SetCommunityListLinePermit");
      String hostname = _configuration.getHostname();
      for (CommunityList list : _configuration.getCommunityLists().values()) {
         String listName = hostname + ":" + list.getName();
         List<CommunityListLine> lineList = list.getLines();
         for (int i = 0; i < lineList.size(); i++) {
            CommunityListLine line = lineList.get(i);
            for (Long community : line.getMatchingCommunities(_allCommunities)) {
               wSetCommunityListLine.append(listName + "|" + i + "|"
                     + community + "\n");
            }
            switch (line.getAction()) {
            case ACCEPT:
               wSetCommunityListLinePermit.append(listName + "|" + i + "\n");
               break;

            case REJECT:
               break;

            default:
               throw new BatfishException("bad action");
            }
         }
      }
   }

   public void writeFacts() {
      writeVendor();
      writeInterfaces();
      writeIpAccessLists();
      writeSetActiveInt();
      writeSetIpInt();
      writeSwitchportSettings();
      writeOspfInterfaces();
      writeStaticRoutes();
      writeBgpNeighborPolicies();
      writeOspfOutboundPolicyMaps();
      writeOspfGeneratedRoutes();
      writeRouteReflectorClients();
      writeOspfRouterId();
      writeLinkLoadLimits();
      writePolicyMaps();
      writeBgpNeighbors();
      writeRouteFilters();
      writeOriginationPolicies();
      writeCommunityLists();
      writeBgpGeneratedRoutes();
      writeBgpNeighborGeneratedRoutes();
      writeGeneratedRoutes();
      writeVlanInterface();
      writeToJson();
   }

   private void writeGeneratedRoutes() {
      StringBuilder wSetGeneratedRoute_flat = _factBins
            .get("SetGeneratedRoute_flat");
      StringBuilder wSetGeneratedRoutePolicy_flat = _factBins
            .get("SetGeneratedRoutePolicy_flat");
      String hostname = _configuration.getHostname();
      for (GeneratedRoute gr : _configuration.getGeneratedRoutes()) {
         long network_start = gr.getPrefix().asLong();
         int prefix_length = gr.getPrefixLength();
         long network_end = Util.getNetworkEnd(network_start, prefix_length);
         wSetGeneratedRoute_flat.append(hostname + "|" + network_start + "|"
               + network_end + "|" + prefix_length + "|"
               + gr.getAdministrativeCost() + "\n");
         for (PolicyMap grPolicy : gr.getGenerationPolicies()) {
            String policyName = hostname + ":" + grPolicy.getMapName();
            wSetGeneratedRoutePolicy_flat.append(hostname + "|" + network_start
                  + "|" + network_end + "|" + prefix_length + "|" + policyName
                  + "\n");
         }
      }
   }

   private void writeInterfaces() {
      StringBuilder wSetFakeInterface = _factBins.get("SetFakeInterface");
      StringBuilder wSetFlowSinkInterface = _factBins
            .get("SetFlowSinkInterface");
      StringBuilder wSetOspfInterfaceCost = _factBins
            .get("SetOspfInterfaceCost");
      StringBuilder wSetInterfaceFilterIn = _factBins
            .get("SetInterfaceFilterIn");
      StringBuilder wSetInterfaceFilterOut = _factBins
            .get("SetInterfaceFilterOut");
      StringBuilder wSetInterfaceRoutingPolicy = _factBins
            .get("SetInterfaceRoutingPolicy");
      String hostname = _configuration.getHostname();
      for (Interface i : _configuration.getInterfaces().values()) {
         String interfaceName = i.getName();

         // flow sinks
         if (interfaceName.startsWith(FLOW_SINK_INTERFACE_PREFIX)) {
            wSetFlowSinkInterface.append(hostname + "|" + interfaceName + "\n");
         }

         // fake interfaces
         if (interfaceName.startsWith(FLOW_SINK_INTERFACE_PREFIX)
               || interfaceName.startsWith(Synthesizer.FAKE_INTERFACE_PREFIX)) {
            wSetFakeInterface.append(hostname + "|" + interfaceName + "\n");
         }

         OspfProcess proc = _configuration.getOspfProcess();
         // TODO: support ospf running on vlan interface properly
         if (proc != null) {
            Integer ospfCost = i.getOspfCost();
            if (ospfCost == null) {
               if (interfaceName.startsWith("Vlan")) {
                  // TODO: fix for non-cisco
                  ospfCost = DEFAULT_CISCO_VLAN_OSPF_COST;
               }
               else {
                  ospfCost = Math.max((int) (_configuration.getOspfProcess()
                        .getReferenceBandwidth() / i.getBandwidth()), 1);
               }
            }
            wSetOspfInterfaceCost.append(_configuration.getHostname() + "|"
                  + interfaceName + "|" + ospfCost + "\n");
         }
         IpAccessList incomingFilter = i.getIncomingFilter();
         if (incomingFilter != null) {
            String filterName = hostname + ":" + incomingFilter.getName();
            wSetInterfaceFilterIn.append(hostname + "|" + interfaceName + "|"
                  + filterName + "\n");
         }
         IpAccessList outgoingFilter = i.getOutgoingFilter();
         if (outgoingFilter != null) {
            String filterName = hostname + ":" + outgoingFilter.getName();
            wSetInterfaceFilterOut.append(hostname + "|" + interfaceName + "|"
                  + filterName + "\n");
         }
         PolicyMap routingPolicy = i.getRoutingPolicy();
         if (routingPolicy != null) {
            String policyName = hostname + ":" + routingPolicy.getMapName();
            wSetInterfaceRoutingPolicy.append(hostname + "|" + interfaceName
                  + "|" + policyName + "\n");
         }
      }
   }

   private void writeIpAccessLists() {
      StringBuilder wSetIpAccessListDenyLine = _factBins
            .get("SetIpAccessListDenyLine");
      StringBuilder wSetIpAccessListLine = _factBins.get("SetIpAccessListLine");
      StringBuilder wSetIpAccessListLine_dstPortRange = _factBins
            .get("SetIpAccessListLine_dstPortRange");
      StringBuilder wSetIpAccessListLine_srcPortRange = _factBins
            .get("SetIpAccessListLine_srcPortRange");
      for (IpAccessList ipAccessList : _configuration.getIpAccessLists()
            .values()) {
         String name = _configuration.getHostname() + ":"
               + ipAccessList.getName();
         List<IpAccessListLine> lines = ipAccessList.getLines();
         for (int i = 0; i < lines.size(); i++) {
            IpAccessListLine line = lines.get(i);
            if (!line.isValid()) {
               _warnings
                     .add("WARNING: IpAccessList "
                           + name
                           + " line "
                           + i
                           + ": ignored (will never be matched) because we do not know how to handle non-trailing wildcard bits\n");
               continue;
            }
            int protocol = line.getProtocol();
            long dstIpStart = line.getDestinationIP().asLong();
            long dstIpEnd = line.getDestinationIP()
                  .getWildcardEndIp(line.getDestinationWildcard()).asLong();
            long srcIpStart = line.getSourceIP().asLong();
            long srcIpEnd = line.getSourceIP()
                  .getWildcardEndIp(line.getSourceWildcard()).asLong();
            wSetIpAccessListLine.append(name + "|" + i + "|" + protocol + "|"
                  + srcIpStart + "|" + srcIpEnd + "|" + dstIpStart + "|"
                  + dstIpEnd + "\n");
            switch (line.getAction()) {
            case ACCEPT:
               break;

            case REJECT:
               wSetIpAccessListDenyLine.append(name + "|" + i + "\n");
               break;

            default:
               throw new BatfishException("bad action");
            }
            String protocolName = Util.getProtocolName(protocol);
            if (protocolName.equals("tcp") || protocolName.equals("udp")) {
               for (SubRange dstPortRange : line.getDstPortRanges()) {
                  int startPort = dstPortRange.getStart();
                  int endPort = dstPortRange.getEnd();
                  wSetIpAccessListLine_dstPortRange.append(name + "|" + i + "|"
                        + startPort + "|" + endPort + "\n");
               }
               for (SubRange srcPortRange : line.getSrcPortRanges()) {
                  int startPort = srcPortRange.getStart();
                  int endPort = srcPortRange.getEnd();
                  wSetIpAccessListLine_srcPortRange.append(name + "|" + i + "|"
                        + startPort + "|" + endPort + "\n");
               }
            }
         }
      }
   }

   private void writeLinkLoadLimits() {
      StringBuilder wSetLinkLoadLimitIn = _factBins.get("SetLinkLoadLimitIn");
      StringBuilder wSetLinkLoadLimitOut = _factBins.get("SetLinkLoadLimitOut");
      String hostname = _configuration.getHostname();
      for (Interface iface : _configuration.getInterfaces().values()) {
         if (iface.getName().startsWith("Vlan")) { // TODO: deal with vlans
            continue;
         }
         double limit = iface.getBandwidth();
         String interfaceName = iface.getName();
         wSetLinkLoadLimitIn.append(hostname + "|" + interfaceName + "|"
               + limit + "\n");
         wSetLinkLoadLimitOut.append(hostname + "|" + interfaceName + "|"
               + limit + "\n");
      }
   }

   private void writeOriginationPolicies() {
      StringBuilder wSetBgpOriginationPolicy = _factBins
            .get("SetBgpOriginationPolicy");
      String hostname = _configuration.getHostname();
      BgpProcess proc = _configuration.getBgpProcess();
      if (proc != null) {
         for (BgpNeighbor neighbor : proc.getNeighbors().values()) {
            for (PolicyMap originationPolicy : neighbor
                  .getOriginationPolicies()) {
               String policyName = hostname + ":"
                     + originationPolicy.getMapName();
               Long neighborIp = neighbor.getAddress().asLong();
               wSetBgpOriginationPolicy.append(hostname + "|" + neighborIp
                     + "|" + policyName + "\n");
            }
         }
      }
   }

   private void writeOspfGeneratedRoutes() {
      StringBuilder wSetOspfGeneratedRoute_flat = _factBins
            .get("SetOspfGeneratedRoute_flat");
      StringBuilder wSetOspfGeneratedRoutePolicy_flat = _factBins
            .get("SetOspfGeneratedRoutePolicy_flat");
      String hostname = _configuration.getHostname();
      OspfProcess proc = _configuration.getOspfProcess();
      if (proc != null) {
         for (GeneratedRoute gr : proc.getGeneratedRoutes()) {
            long network_start = gr.getPrefix().asLong();
            int prefix_length = gr.getPrefixLength();
            long network_end = Util.getNetworkEnd(network_start, prefix_length);
            wSetOspfGeneratedRoute_flat.append(hostname + "|" + network_start
                  + "|" + network_end + "|" + prefix_length + "\n");
            for (PolicyMap generationPolicy : gr.getGenerationPolicies()) {
               String gpName = hostname + ":" + generationPolicy.getMapName();
               wSetOspfGeneratedRoutePolicy_flat.append(hostname + "|"
                     + network_start + "|" + network_end + "|" + prefix_length
                     + "|" + gpName + "\n");
            }
         }
      }
   }

   public void writeToJson() {
      String hostname = _configuration.getHostname();
      OspfProcess proc = _configuration.getOspfProcess();
      JSONObject jsonObject = new JSONObject();
      jsonObject.put("hostname", hostname);
      String filename = hostname + ".json";

      if (proc != null) {
         JSONObject jProc = proc.getJSON();
         JSONArray jAreas = new JSONArray();
         for (OspfArea area : proc.getAreas().values()) {
            JSONObject jArea = new JSONObject();
            jArea.put("areaNumber", area.getNumber());
            JSONArray jInterfaces = new JSONArray();
            for (Interface i : area.getInterfaces()) {
               jInterfaces.add((Object)i.getJSON());
            }
            jArea.put("interfaces", (Object)jInterfaces);
            jAreas.add((Object)jArea);
         }
         jProc.put("areas", (Object)jAreas);
         jsonObject.put("OspfProcess", (Object)jProc);
      }

      // writing the JSONObject into a file(info.json)
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

   private void writeOspfInterfaces() {
      StringBuilder wSetOspfInterface = _factBins.get("SetOspfInterface");
      String hostname = _configuration.getHostname();
      OspfProcess proc = _configuration.getOspfProcess();
      if (proc != null) {
         for (OspfArea area : proc.getAreas().values()) {
            for (Interface i : area.getInterfaces()) {
               String interfaceName = i.getName();
               wSetOspfInterface.append(hostname + "|" + interfaceName + "|"
                     + area.getNumber() + "\n");
            }
         }
      }
   }

   private void writeOspfOutboundPolicyMaps() {
      StringBuilder wSetOspfOutboundPolicyMap = _factBins
            .get("SetOspfOutboundPolicyMap");
      StringBuilder wSetPolicyMapOspfExternalRouteType = _factBins
            .get("SetPolicyMapOspfExternalRouteType");
      String hostname = _configuration.getHostname();
      OspfProcess proc = _configuration.getOspfProcess();
      if (proc != null) {
         for (PolicyMap map : proc.getOutboundPolicyMaps()) {
            String mapName = hostname + ":" + map.getMapName();
            OspfMetricType metricType = proc.getPolicyMetricTypes().get(map);
            String protocol = null;
            switch (metricType) {
            case E1:
               protocol = "ospfE1";
               break;
            case E2:
               protocol = "ospfE2";
               break;
            default:
               throw new BatfishException("invalid ospf metric type");
            }
            wSetOspfOutboundPolicyMap.append(hostname + "|" + mapName + "\n");
            wSetPolicyMapOspfExternalRouteType.append(mapName + "|" + protocol
                  + "\n");
         }
      }
   }

   private void writeOspfRouterId() {
      StringBuilder wSetOspfRouterId = _factBins.get("SetOspfRouterId");
      String hostname = _configuration.getHostname();
      OspfProcess proc = _configuration.getOspfProcess();
      if (proc != null) {
         String id = proc.getRouterId();
         if (id != null) {
            wSetOspfRouterId.append(hostname + "|" + Util.ipToLong(id) + "\n");
         }
      }
   }

   private void writePolicyMaps() {
      StringBuilder wSetPolicyMapClauseAddCommunity = _factBins
            .get("SetPolicyMapClauseAddCommunity");
      StringBuilder wSetPolicyMapClauseDeleteCommunity = _factBins
            .get("SetPolicyMapClauseDeleteCommunity");
      StringBuilder wSetPolicyMapClauseDeny = _factBins
            .get("SetPolicyMapClauseDeny");
      StringBuilder wSetPolicyMapClauseMatchAcl = _factBins
            .get("SetPolicyMapClauseMatchAcl");
      StringBuilder wSetPolicyMapClauseMatchCommunityList = _factBins
            .get("SetPolicyMapClauseMatchCommunityList");
      StringBuilder wSetPolicyMapClauseMatchNeighbor = _factBins
            .get("SetPolicyMapClauseMatchNeighbor");
      StringBuilder wSetPolicyMapClauseMatchProtocol = _factBins
            .get("SetPolicyMapClauseMatchProtocol");
      StringBuilder wSetPolicyMapClauseMatchRouteFilter = _factBins
            .get("SetPolicyMapClauseMatchRouteFilter");
      StringBuilder wSetPolicyMapClausePermit = _factBins
            .get("SetPolicyMapClausePermit");
      StringBuilder wSetPolicyMapClauseSetCommunity = _factBins
            .get("SetPolicyMapClauseSetCommunity");
      StringBuilder wSetPolicyMapClauseSetLocalPreference = _factBins
            .get("SetPolicyMapClauseSetLocalPreference");
      StringBuilder wSetPolicyMapClauseSetMetric = _factBins
            .get("SetPolicyMapClauseSetMetric");
      StringBuilder wSetPolicyMapClauseMatchTag = _factBins
            .get("SetPolicyMapClauseMatchTag");
      StringBuilder wSetPolicyMapClauseSetNextHopIp = _factBins
            .get("SetPolicyMapClauseSetNextHopIp");
      StringBuilder wSetPolicyMapClauseSetOriginType = _factBins
            .get("SetPolicyMapClauseSetOriginType");
      String hostname = _configuration.getHostname();
      for (PolicyMap map : _configuration.getPolicyMaps().values()) {
         String mapName = hostname + ":" + map.getMapName();
         List<PolicyMapClause> clauses = map.getClauses();
         for (int i = 0; i < clauses.size(); i++) {
            PolicyMapClause clause = clauses.get(i);
            // match lines
            // TODO: complete
            switch (clause.getAction()) {
            case DENY:
               wSetPolicyMapClauseDeny.append(mapName + "|" + i + "\n");
               break;
            case PERMIT:
               wSetPolicyMapClausePermit.append(mapName + "|" + i + "\n");
               break;
            default:
               throw new BatfishException("invalid action");
            }
            for (PolicyMapMatchLine matchLine : clause.getMatchLines()) {
               switch (matchLine.getType()) {
               case AS_PATH_ACCESS_LIST:
                  // TODO: implement
                  // throw new BatfishException("not implemented");
                  _warnings
                        .add("WARNING: "
                              + mapName
                              + ":"
                              + i
                              + ": Policy map matching of AS path acls not implemented!\n");
                  break;

               case COMMUNITY_LIST:
                  PolicyMapMatchCommunityListLine mclLine = (PolicyMapMatchCommunityListLine) matchLine;
                  for (CommunityList cList : mclLine.getLists()) {
                     String cListName = hostname + ":" + cList.getName();
                     wSetPolicyMapClauseMatchCommunityList.append(mapName + "|"
                           + i + "|" + cListName + "\n");
                  }
                  break;

               case IP_ACCESS_LIST:
                  PolicyMapMatchIpAccessListLine mialLine = (PolicyMapMatchIpAccessListLine) matchLine;
                  for (IpAccessList list : mialLine.getLists()) {
                     String listName = hostname + ":" + list.getName();
                     wSetPolicyMapClauseMatchAcl.append(mapName + "|" + i + "|"
                           + listName + "\n");
                  }
                  break;

               case NEIGHBOR:
                  PolicyMapMatchNeighborLine pmmnl = (PolicyMapMatchNeighborLine) matchLine;
                  long neighborIp = pmmnl.getNeighborIp().asLong();
                  wSetPolicyMapClauseMatchNeighbor.append(mapName + "|" + i
                        + "|" + neighborIp + "\n");
                  break;

               case PROTOCOL:
                  PolicyMapMatchProtocolLine pmmpl = (PolicyMapMatchProtocolLine) matchLine;
                  for (Protocol prot : pmmpl.getProtocols()) {
                     wSetPolicyMapClauseMatchProtocol.append(mapName + "|" + i
                           + "|" + getLBRoutingProtocol(prot) + "\n");
                  }
                  break;

               case ROUTE_FILTER_LIST:
                  PolicyMapMatchRouteFilterListLine mrfLine = (PolicyMapMatchRouteFilterListLine) matchLine;
                  for (RouteFilterList rfList : mrfLine.getLists()) {
                     String rflName = hostname + ":" + rfList.getName();
                     wSetPolicyMapClauseMatchRouteFilter.append(mapName + "|"
                           + i + "|" + rflName + "\n");
                  }
                  break;

               case TAG:
                  PolicyMapMatchTagLine pmmtl = (PolicyMapMatchTagLine) matchLine;
                  for (Integer tag : pmmtl.getTags()) {
                     wSetPolicyMapClauseMatchTag.append(mapName + "|" + i + "|"
                           + tag + "\n");
                  }
                  break;

               default:
                  throw new BatfishException("invalid match type");
               }
            }

            // set lines
            for (PolicyMapSetLine setLine : clause.getSetLines()) {
               switch (setLine.getType()) {
               case ADDITIVE_COMMUNITY:
                  PolicyMapSetAddCommunityLine sacLine = (PolicyMapSetAddCommunityLine) setLine;
                  for (Long community : sacLine.getCommunities()) {
                     wSetPolicyMapClauseAddCommunity.append(mapName + "|" + i
                           + "|" + community + "\n");
                  }
                  break;

               case AS_PATH_PREPEND:
                  // TODO: implement
                  // throw new BatfishException("not implemented");
                  _warnings.add("WARNING: " + mapName + ":" + i
                        + ": AS_PATH_PREPEND not implemented\n");
                  break;

               case COMMUNITY:
                  PolicyMapSetCommunityLine scLine = (PolicyMapSetCommunityLine) setLine;
                  for (Long community : scLine.getCommunities()) {
                     wSetPolicyMapClauseSetCommunity.append(mapName + "|" + i
                           + "|" + community + "\n");
                  }
                  break;

               case COMMUNITY_NONE:
                  // TODO: implement
                  throw new BatfishException("not implemented");

               case DELETE_COMMUNITY:
                  PolicyMapSetDeleteCommunityLine sdcLine = (PolicyMapSetDeleteCommunityLine) setLine;
                  String cListName = hostname + ":"
                        + sdcLine.getList().getName();
                  wSetPolicyMapClauseDeleteCommunity.append(mapName + "|" + i
                        + "|" + cListName + "\n");
                  break;

               case LOCAL_PREFERENCE:
                  PolicyMapSetLocalPreferenceLine pmslp = (PolicyMapSetLocalPreferenceLine) setLine;
                  int localPref = pmslp.getLocalPreference();
                  wSetPolicyMapClauseSetLocalPreference.append(mapName + "|"
                        + i + "|" + localPref + "\n");
                  break;

               case METRIC:
                  PolicyMapSetMetricLine pmsml = (PolicyMapSetMetricLine) setLine;
                  int metric = pmsml.getMetric();
                  wSetPolicyMapClauseSetMetric.append(mapName + "|" + i + "|"
                        + metric + "\n");
                  break;

               case NEXT_HOP:
                  PolicyMapSetNextHopLine pmsnhl = (PolicyMapSetNextHopLine) setLine;
                  for (Ip nextHopIp : pmsnhl.getNextHops()) {
                     wSetPolicyMapClauseSetNextHopIp.append(mapName + "|" + i
                           + "|" + nextHopIp.asLong() + "\n");
                  }
                  break;

               case ORIGIN_TYPE:
                  PolicyMapSetOriginTypeLine pmsotl = (PolicyMapSetOriginTypeLine) setLine;
                  OriginType originType = pmsotl.getOriginType();
                  wSetPolicyMapClauseSetOriginType.append(mapName + "|" + i
                        + "|" + originType.toString() + "\n");
                  break;

               default:
                  throw new BatfishException("invalid set type");
               }
            }
         }

      }
   }

   private void writeRouteFilters() {
      StringBuilder wSetRouteFilterLine = _factBins.get("SetRouteFilterLine");
      StringBuilder wSetRouteFilterPermitLine = _factBins
            .get("SetRouteFilterPermitLine");
      String hostname = _configuration.getHostname();
      for (RouteFilterList filter : _configuration.getRouteFilterLists()
            .values()) {
         String filterName = hostname + ":" + filter.getName();
         List<RouteFilterLine> lines = filter.getLines();
         for (int i = 0; i < lines.size(); i++) {
            RouteFilterLine line = lines.get(i);
            switch (line.getType()) {
            case LENGTH_RANGE:
               RouteFilterLengthRangeLine lrLine = (RouteFilterLengthRangeLine) line;
               int prefix_length = lrLine.getPrefixLength();
               Long network_start = lrLine.getPrefix().asLong();
               Long network_end = Util.getNetworkEnd(network_start,
                     prefix_length);
               SubRange prefixRange = lrLine.getLengthRange();
               int min_prefix = prefixRange.getStart();
               int max_prefix = prefixRange.getEnd();
               wSetRouteFilterLine.append(filterName + "|" + i + "|"
                     + network_start + "|" + network_end + "|" + min_prefix
                     + "|" + max_prefix + "\n");
               switch (lrLine.getAction()) {
               case ACCEPT:
                  wSetRouteFilterPermitLine.append(filterName + "|" + i + "\n");
                  break;

               case REJECT:
                  break;

               default:
                  throw new BatfishException("bad action");
               }
               break;

            case THROUGH:
               // throw new BatfishException("not implemented");
               _warnings.add("WARNING: " + filterName + ":" + i
                     + ": route-filter through not implemented\n");
               break;

            default:
               throw new BatfishException("bad line type");
            }

         }
      }
   }

   private void writeRouteReflectorClients() {
      StringBuilder wSetRouteReflectorClient = _factBins
            .get("SetRouteReflectorClient");
      String hostname = _configuration.getHostname();
      BgpProcess proc = _configuration.getBgpProcess();
      if (proc != null) {
         for (BgpNeighbor neighbor : proc.getNeighbors().values()) {
            Long clusterId = neighbor.getClusterId();
            if (clusterId == null) {
               continue;
            }
            Ip neighborIp = neighbor.getAddress();
            wSetRouteReflectorClient.append(hostname + "|"
                  + neighborIp.asLong() + "|" + clusterId + "\n");
         }
      }
   }

   private void writeSetActiveInt() {
      StringBuilder wSetActiveInt = _factBins.get("SetActiveInt");
      String hostname = _configuration.getHostname();
      for (Interface i : _configuration.getInterfaces().values()) {
         String interfaceName = i.getName();
         boolean active = i.getActive();
         if (active) {
            wSetActiveInt.append(hostname + "|" + interfaceName + "\n");
         }
      }
   }

   private void writeSetIpInt() {
      String hostname = _configuration.getHostname();
      StringBuilder wSetNetwork = _factBins.get("SetNetwork");
      StringBuilder wSetIpInt = _factBins.get("SetIpInt");
      for (Interface i : _configuration.getInterfaces().values()) {
         String interfaceName = i.getName();
         Ip ip = i.getIP();
         Ip subnetMask = i.getSubnetMask();
         if (ip != null) {
            long ipInt = ip.asLong();
            long subnet = subnetMask.asLong();
            int prefix_length = subnetMask.numSubnetBits();
            long network_start = ipInt & subnet;
            long network_end = Util.getNetworkEnd(network_start, prefix_length);
            wSetNetwork.append(network_start + "|" + network_start + "|"
                  + network_end + "|" + prefix_length + "\n");
            wSetIpInt.append(hostname + "|" + interfaceName + "|" + ip.asLong()
                  + "|" + prefix_length + "\n");
         }
      }
   }

   private void writeStaticRoutes() {
      StringBuilder wSetNetwork = _factBins.get("SetNetwork");
      StringBuilder wSetStaticRoute_flat = _factBins.get("SetStaticRoute_flat");
      StringBuilder wSetStaticIntRoute_flat = _factBins
            .get("SetStaticIntRoute_flat");
      String hostName = _configuration.getHostname();
      for (StaticRoute route : _configuration.getStaticRoutes()) {
         Ip prefix = route.getPrefix();
         Ip nextHopIp = route.getNextHopIp();
         if (nextHopIp == null) {
            nextHopIp = new Ip(0);
         }
         int prefix_length = route.getPrefixLength();
         long network_start = prefix.asLong();
         long network_end = Util.getNetworkEnd(network_start, prefix_length);
         int distance = route.getDistance();
         int tag = route.getTag();
         String nextHopInt = route.getNextHopInterface();
         wSetNetwork.append(network_start + "|" + network_start + "|"
               + network_end + "|" + prefix_length + "\n");
         if (nextHopInt != null) { // use next hop interface instead
            if (Util.isNullInterface(nextHopInt)) {
               nextHopInt = Util.NULL_INTERFACE_NAME;
            }
            wSetStaticIntRoute_flat.append(hostName + "|" + network_start + "|"
                  + network_end + "|" + prefix_length + "|"
                  + nextHopIp.asLong() + "|" + nextHopInt + "|" + distance
                  + "|" + tag + "\n");
         }
         else {
            wSetStaticRoute_flat.append(hostName + "|" + network_start + "|"
                  + network_end + "|" + prefix_length + "|"
                  + nextHopIp.asLong() + "|" + distance + "|" + tag + "\n");
         }
      }
   }

   private void writeSwitchportSettings() {
      StringBuilder wSetSwitchportAccess = _factBins.get("SetSwitchportAccess");
      StringBuilder wSetSwitchportTrunkNative = _factBins
            .get("SetSwitchportTrunkNative");
      StringBuilder wSetSwitchportTrunkAllows = _factBins
            .get("SetSwitchportTrunkAllows");
      StringBuilder wSetSwitchportTrunkEncapsulation = _factBins
            .get("SetSwitchportTrunkEncapsulation");
      String hostname = _configuration.getHostname();
      for (Interface i : _configuration.getInterfaces().values()) {
         String interfaceName = i.getName();
         switch (i.getSwitchportMode()) {
         case ACCESS:
            int vlan = i.getAccessVlan();
            wSetSwitchportAccess.append(hostname + "|" + interfaceName + "|"
                  + vlan + "\n");
            break;

         // TODO: create derived switchport facts in logic and here
         case DYNAMIC_AUTO:
         case DYNAMIC_DESIRABLE:
         case NONE:
            break;

         case TRUNK:
            SwitchportEncapsulationType encapsulation = i
                  .getSwitchportTrunkEncapsulation();
            wSetSwitchportTrunkEncapsulation.append(hostname + "|"
                  + interfaceName + "|"
                  + encapsulation.toString().toLowerCase() + "\n");
            int nativeVlan = i.getNativeVlan();
            wSetSwitchportTrunkNative.append(hostname + "|" + interfaceName
                  + "|" + nativeVlan + "\n");
            for (SubRange range : i.getAllowedVlans()) {
               wSetSwitchportTrunkAllows.append(hostname + "|" + interfaceName
                     + "|" + range.getStart() + "|" + range.getEnd() + "\n");
            }
            break;

         default:
            throw new BatfishException("invalid switchport mode");
         }
      }
   }

   private void writeVendor() {
      String hostname = _configuration.getHostname();
      String vendor = _configuration.getVendor();
      StringBuilder wSetNodeVendor = _factBins.get("SetNodeVendor");
      wSetNodeVendor.append(hostname + "|" + vendor + "\n");
   }

   private void writeVlanInterface() {
      StringBuilder wSetVlanInterface = _factBins.get("SetVlanInterface");
      String hostname = _configuration.getHostname();
      for (String ifaceName : _configuration.getInterfaces().keySet()) {
         Integer vlan = Util.getInterfaceVlanNumber(ifaceName);
         if (vlan != null) {
            wSetVlanInterface.append(hostname + "|" + ifaceName + "|" + vlan
                  + "\n");
         }
      }
   }

}
