package org.batfish.representation.cisco;

import java.util.ArrayList;
import java.util.List;
import org.batfish.common.util.DefinedStructure;

public class IpAsPathAccessList extends DefinedStructure<String> {

  private static final long serialVersionUID = 1L;

  private List<IpAsPathAccessListLine> _lines;

  public IpAsPathAccessList(String name, int definitionLine) {
    super(name, definitionLine);
    _lines = new ArrayList<>();
  }

  public void addLine(IpAsPathAccessListLine line) {
    _lines.add(line);
  }

  public List<IpAsPathAccessListLine> getLines() {
    return _lines;
  }
}
