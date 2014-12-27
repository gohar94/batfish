package batfish.representation.juniper;

import java.io.Serializable;

import batfish.representation.Configuration;
import batfish.representation.PolicyMapClause;

public abstract class PsFrom implements Serializable {

   /**
    *
    */
   private static final long serialVersionUID = 1L;

   public abstract void applyTo(PolicyMapClause clause, Configuration c);

}