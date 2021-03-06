package org.batfish.grammar.routing_table.nxos;

import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.batfish.common.BatfishException;
import org.batfish.common.Warnings;
import org.batfish.common.plugin.IBatfish;
import org.batfish.common.util.CommonUtil;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Interface;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.Route;
import org.batfish.datamodel.RouteBuilder;
import org.batfish.datamodel.RoutingProtocol;
import org.batfish.datamodel.collections.RoutesByVrf;
import org.batfish.grammar.RoutingTableExtractor;
import org.batfish.grammar.routing_table.nxos.NxosRoutingTableParser.NetworkContext;
import org.batfish.grammar.routing_table.nxos.NxosRoutingTableParser.Nxos_routing_tableContext;
import org.batfish.grammar.routing_table.nxos.NxosRoutingTableParser.ProtocolContext;
import org.batfish.grammar.routing_table.nxos.NxosRoutingTableParser.RouteContext;
import org.batfish.grammar.routing_table.nxos.NxosRoutingTableParser.Vrf_declarationContext;

public class NxosRoutingTableExtractor extends NxosRoutingTableParserBaseListener
    implements RoutingTableExtractor {

  private static int toInteger(Token t) {
    return Integer.parseInt(t.getText());
  }

  private Prefix _currentPrefix;

  private String _currentVrfName;

  private SortedSet<Route> _currentVrfRoutes;

  private final String _hostname;

  private final Map<Ip, String> _ipOwners;

  @SuppressWarnings("unused")
  private NxosRoutingTableCombinedParser _parser;

  private RoutesByVrf _routesByVrf;

  private final String _text;

  @SuppressWarnings("unused")
  private final Warnings _w;

  public NxosRoutingTableExtractor(
      String hostname,
      String text,
      NxosRoutingTableCombinedParser parser,
      Warnings w,
      IBatfish batfish) {
    _hostname = hostname;
    _text = text;
    _parser = parser;
    _w = w;
    Map<String, Configuration> configurations = batfish.loadConfigurations();
    Map<Ip, String> ipOwnersSimple = CommonUtil.computeIpOwnersSimple(configurations, true);
    _ipOwners = ipOwnersSimple;
  }

  private BatfishException convError(Class<?> type, ParserRuleContext ctx) {
    String typeName = type.getSimpleName();
    String txt = getFullText(ctx);
    return new BatfishException("Could not convert to " + typeName + ": " + txt);
  }

  @Override
  public void enterNetwork(NetworkContext ctx) {
    _currentPrefix = new Prefix(ctx.IP_PREFIX().getText());
  }

  @Override
  public void enterNxos_routing_table(Nxos_routing_tableContext ctx) {
    _routesByVrf = new RoutesByVrf();
  }

  @Override
  public void enterVrf_declaration(Vrf_declarationContext ctx) {
    String currentVrfName = ctx.double_quoted_string().ID().getText();
    initVrf(currentVrfName);
  }

  @Override
  public void exitRoute(RouteContext ctx) {
    if (ctx.protocol().LOCAL() != null) {
      return;
    }
    if (_currentVrfRoutes == null) {
      initVrf(Configuration.DEFAULT_VRF_NAME);
    }
    RoutingProtocol protocol = toProtocol(ctx.protocol());
    String nextHopInterface = Route.UNSET_NEXT_HOP_INTERFACE;
    if (ctx.nexthopint != null) {
      nextHopInterface = ctx.nexthopint.getText();
    }
    int admin = toInteger(ctx.admin);
    int cost = toInteger(ctx.cost);
    Ip nextHopIp = Route.UNSET_ROUTE_NEXT_HOP_IP;
    if (protocol != RoutingProtocol.CONNECTED && ctx.nexthop != null) {
      nextHopIp = new Ip(ctx.nexthop.getText());
    }

    RouteBuilder rb = new RouteBuilder();
    rb.setNode(_hostname);
    rb.setNetwork(_currentPrefix);
    if (protocol == RoutingProtocol.CONNECTED
        || (protocol == RoutingProtocol.STATIC && nextHopIp.equals(Route.UNSET_ROUTE_NEXT_HOP_IP))
        || Interface.NULL_INTERFACE_NAME.equals(nextHopInterface)) {
      rb.setNextHop(Configuration.NODE_NONE_NAME);
    }
    if (!nextHopIp.equals(Route.UNSET_ROUTE_NEXT_HOP_IP)) {
      rb.setNextHopIp(nextHopIp);
      String nextHop = _ipOwners.get(nextHopIp);
      if (nextHop != null) {
        rb.setNextHop(nextHop);
      }
    }
    if (nextHopInterface != null) {
      rb.setNextHopInterface(nextHopInterface);
    }
    rb.setAdministrativeCost(admin);
    rb.setCost(cost);
    rb.setProtocol(protocol);
    rb.setTag(Route.UNSET_ROUTE_TAG);
    rb.setVrf(_currentVrfName);
    Route route = rb.build();
    _currentVrfRoutes.add(route);
  }

  private String getFullText(ParserRuleContext ctx) {
    int start = ctx.getStart().getStartIndex();
    int end = ctx.getStop().getStopIndex();
    String text = _text.substring(start, end + 1);
    return text;
  }

  @Override
  public RoutesByVrf getRoutesByVrf() {
    return _routesByVrf;
  }

  private void initVrf(String name) {
    _currentVrfName = name;
    _currentVrfRoutes = new TreeSet<>();
    _routesByVrf.put(name, _currentVrfRoutes);
  }

  @Override
  public void processParseTree(ParserRuleContext tree) {
    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(this, tree);
  }

  private RoutingProtocol toProtocol(ProtocolContext ctx) {
    if (ctx.BGP() != null && ctx.INTERNAL() != null) {
      return RoutingProtocol.IBGP;
    } else if (ctx.BGP() != null && ctx.EXTERNAL() != null) {
      return RoutingProtocol.BGP;
    } else if (ctx.DIRECT() != null) {
      return RoutingProtocol.CONNECTED;
    } else if (ctx.LOCAL() != null) {
      return RoutingProtocol.CONNECTED;
    } else if (ctx.INTRA() != null) {
      return RoutingProtocol.OSPF;
    } else if (ctx.INTER() != null) {
      return RoutingProtocol.OSPF_IA;
    } else if (ctx.TYPE_1() != null) {
      return RoutingProtocol.OSPF_E1;
    } else if (ctx.TYPE_2() != null) {
      return RoutingProtocol.OSPF_E2;
    } else if (ctx.STATIC() != null) {
      return RoutingProtocol.STATIC;
    } else {
      throw convError(RoutingProtocol.class, ctx);
    }
  }
}
