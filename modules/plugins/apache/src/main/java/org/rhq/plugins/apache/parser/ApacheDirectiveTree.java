package org.rhq.plugins.apache.parser;

import java.util.ArrayList;
import java.util.List;

public class ApacheDirectiveTree {

    private ApacheDirective rootNode;
    
    public ApacheDirectiveTree(){
       rootNode = new ApacheDirective();
       rootNode.setRootNode(true);
    }

    public ApacheDirective getRootNode() {
        return rootNode;
    }

    public void setRootNode(ApacheDirective rootNode) {
        this.rootNode = rootNode;
    }
    
    public List<ApacheDirective> search(String name) throws Exception{     
        if (name.startsWith("/"))
          return parseExpr(rootNode,name.substring(1));
        else
          return parseExpr(rootNode,name);
    }
    
    private List<ApacheDirective> parseExpr(ApacheDirective nd, String expr) throws Exception {
        int index = expr.indexOf("/");
        String name;
        
        if (index ==-1)
            name = expr;
        else
           name = expr.substring(0,index);
        
        List<ApacheDirective> nds = new ArrayList<ApacheDirective>();
        
        for (ApacheDirective dir : nd.getChildByName(name)){
            if (index ==-1)
                nds.add(dir);
            else{
              List<ApacheDirective> tempNodes = parseExpr(dir, expr.substring(index+1));
              if (tempNodes != null)
                  nds.addAll(tempNodes);
            }
        }
        
        return nds;
    }
    
}