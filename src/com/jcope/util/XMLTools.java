package com.jcope.util;

import static com.jcope.debug.Debug.assert_;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XMLTools
{
    public static void recursivelyRemove(Node node, short nodeType, String name)
    {
        if (node.getNodeType() == nodeType && (name == null || node.getNodeName().equals(name)))
        {
            node.getParentNode().removeChild(node);
        }
        else
        {
            for (Node cNode : reverseChildren(node))
            {
                recursivelyRemove(cNode, nodeType, name);
            }
        }
    }

    public static int attrLen(Object obj)
    {
        int rval = 0;
        
        if (obj == null)
        {
            // Do Nothing
        }
        else if (obj != null && obj instanceof String[])
        {
            rval = ((String[]) obj).length;
        }
        else
        {
            assert_(false);
        }
        
        return rval;
    }
    
    public static Iterable<Node> children(Node parent)
    {
        return children(parent, true);
    }
    
    public static Iterable<Node> reverseChildren(Node parent)
    {
        return children(parent, false);
    }
    
    private static Iterable<Node> children(Node parent, final boolean forward)
    {
        final NodeList children = parent.getChildNodes();
        final int ub = children.getLength();
        final int[] idxRef = new int[]{0};
        final int inc;
        
        if (forward)
        {
            inc = 1; 
        }
        else
        {
            inc = -1;
            idxRef[0] = ub-1;
        }
        
        Iterable<Node> rval = new Iterable<Node>() {
            
            @Override
            public Iterator<Node> iterator()
            {
                Iterator<Node> rval = new Iterator<Node>() {

                    @Override
                    public boolean hasNext()
                    {
                        return forward ? (idxRef[0] < ub) : (idxRef[0] >= 0);
                    }

                    @Override
                    public Node next()
                    {
                        Node rval;
                        
                        if (hasNext())
                        {
                            rval = children.item(idxRef[0]);
                            idxRef[0] += inc;
                        }
                        else
                        {
                            throw new NoSuchElementException();
                        }
                        
                        return rval;
                    }

                    @Override
                    public void remove()
                    {
                        throw new UnsupportedOperationException();
                    }
                    
                };
                
                return rval;
            }
        };
        
        return rval;
    }
}
