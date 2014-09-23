package com.jcope.util;

import static com.jcope.debug.Debug.assert_;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class XMLTools
{
    public static final String STR_NUM_INDENT_SPACES = ((Integer) ( 2 )).toString();
    private static final HashMap<Short, Boolean> garbageNodes = new HashMap<Short, Boolean>(2);
    
    static
    {
        garbageNodes.put(Node.COMMENT_NODE, Boolean.TRUE);
        garbageNodes.put(Node.TEXT_NODE, Boolean.TRUE);
    }
    
    public static void recursivelyRemove(Node node, short nodeType, String name)
    {
        HashMap<Short, Boolean> nodeTypesToRemove = new HashMap<Short, Boolean>(1);
        nodeTypesToRemove.put(nodeType, Boolean.TRUE);
        recursivelyRemove(node, nodeTypesToRemove, name);
    }

    public static void recursivelyRemove(Node node, HashMap<Short, Boolean> nodeTypesToRemove, String name)
    {
        if (nodeTypesToRemove.get(node.getNodeType()) != null && (name == null || node.getNodeName().equals(name)) && node.getParentNode() != null)
        {
            node.getParentNode().removeChild(node);
        }
        else
        {
            for (Node cNode : reverseChildren(node))
            {
                recursivelyRemove(cNode, nodeTypesToRemove, name);
            }
        }
    }

    public static int attrLen(Object obj)
    {
        int rval;
        
        if (obj == null)
        {
            rval = 0;
        }
        else if (obj instanceof String[])
        {
            rval = ((String[]) obj).length;
        }
        else
        {
            assert_(false);
            // unreachable, but makes compiler happy
            rval = 0;
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
    
    private static Iterable<Node> children(final Node parent, final boolean forward)
    {
        return new Iterable<Node>() {
            
            final boolean m_forward;
            final NodeList children;
            final int ub;
            final int inc;
            int idx;
            
            {
                m_forward = forward;
                children = parent.getChildNodes();
                ub = children.getLength();

                if (m_forward)
                {
                    inc = 1;
                    idx = 0;
                }
                else
                {
                    inc = -1;
                    idx = ub - 1;
                }
            }
            
            @Override
            public Iterator<Node> iterator()
            {
                Iterator<Node> rval = new Iterator<Node>() {

                    @Override
                    public boolean hasNext()
                    {
                        return m_forward ? (idx < ub) : (idx >= 0);
                    }

                    @Override
                    public Node next()
                    {
                        Node rval;
                        
                        if (hasNext())
                        {
                            rval = children.item(idx);
                            idx += inc;
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
    }
    
    public static Document newDoc() throws ParserConfigurationException
    {
        Document rval;
        
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        
        rval = docBuilder.newDocument();
        
        return rval;
    }
    
    public static void writeDoc(Document doc, File file) throws TransformerException
    {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", STR_NUM_INDENT_SPACES);
        DOMSource domSource = new DOMSource(doc);
        
        StreamResult streamResult = new StreamResult(file);
        transformer.transform(domSource, streamResult);
    }
    
    public static Document readDoc(File file) throws ParserConfigurationException, SAXException, IOException
    {
        Document rval;
        
        Node node;
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        rval = dBuilder.parse(file);
        rval.getDocumentElement().normalize();
        
        node = rval.getFirstChild();
        
        if (node != null)
        {
            if (node.getParentNode() != null)
            {
                node = node.getParentNode();
            }
            
            recursivelyRemove(node, garbageNodes, null);
        }
        
        return rval;
    }
}
