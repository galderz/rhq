/*
 * RHQ Management Platform
 * Copyright (C) 2005-2011 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package org.rhq.enterprise.server.sync.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.sync.ExporterMessages;
import org.rhq.enterprise.server.sync.ExportException;
import org.rhq.enterprise.server.sync.ExportWriter;
import org.rhq.enterprise.server.sync.ExportingInputStream;
import org.rhq.enterprise.server.sync.NoSingleEntity;
import org.rhq.enterprise.server.sync.Synchronizer;
import org.rhq.enterprise.server.sync.exporters.AbstractDelegatingExportingIterator;
import org.rhq.enterprise.server.sync.exporters.Exporter;
import org.rhq.enterprise.server.sync.exporters.ExportingIterator;
import org.rhq.enterprise.server.sync.importers.Importer;
import org.rhq.enterprise.server.sync.validators.ConsistencyValidator;

/**
 * 
 *
 * @author Lukas Krejci
 */
@Test
public class ExportingInputStreamTest {

    private static final Log LOG = LogFactory.getLog(ExportingInputStreamTest.class);
    
    private static class ListToStringExporter<T> implements Exporter<NoSingleEntity, T> {

        List<T> valuesToExport;
        
        public static final String NOTE_PREFIX = "Wow, I just exported an item from a list: ";
        
        private class Iterator extends AbstractDelegatingExportingIterator<T, T> {
            public Iterator() {
                super(valuesToExport.iterator());
            }
            
            public void export(ExportWriter output) throws XMLStreamException {
                output.writeStartElement("item");
                output.writeCData(getCurrent().toString());
                output.writeEndElement();
            }
            
            public String getNotes() {
                return NOTE_PREFIX + getCurrent();
            }
            
            protected T convert(T object) {
                return object;
            }
        }
        
        public ListToStringExporter(List<T> valuesToExport) {
            this.valuesToExport = valuesToExport;
        }
                
        public ExportingIterator<T> getExportingIterator() {
            return new Iterator();
        }

        public String getNotes() {
            return valuesToExport.toString();
        }        
    }
    
    private static class ListToStringSynchronizer<T> implements Synchronizer<NoSingleEntity, T> {
        private List<T> list;
        
        public ListToStringSynchronizer(List<T> list) {
            this.list = list;
        }
        
        @Override
        public Exporter<NoSingleEntity, T> getExporter() {
            return new ListToStringExporter<T>(list);
        }
        
        @Override
        public Importer<NoSingleEntity, T> getImporter() {
            return null;
        }
        
        @Override
        public Set<ConsistencyValidator> getRequiredValidators() {
            return Collections.emptySet();            
        }
        
        @Override
        public void initialize(Subject subject, EntityManager entityManager) {
        }
    }
    
    private static class StringListSynchronizer extends ListToStringSynchronizer<String> {
        public StringListSynchronizer(List<String> list) {
            super(list);
        }
    }
    
    private static class IntegerListSynchronizer extends ListToStringSynchronizer<Integer> {
        public IntegerListSynchronizer(List<Integer> list) {
            super(list);
        }
    }
    

    public void testCanExport() throws Exception {
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<Integer> list2 = Arrays.asList(1, 2, 3);
        
        StringListSynchronizer ex1 = new StringListSynchronizer(list1);
        IntegerListSynchronizer ex2 = new IntegerListSynchronizer(list2);
        
        Set<Synchronizer<?, ?>> exporters = this.<Synchronizer<?, ?>>asSet(ex1, ex2);
        
        InputStream export = new ExportingInputStream(exporters, new HashMap<String, ExporterMessages>(), 1024, false);
        
//        String exportContents = readAll(new InputStreamReader(export, "UTF-8"));
//        
//        LOG.info("Export contents:\n" + exportContents);
//        
//        export = new ByteArrayInputStream(exportContents.getBytes("UTF-8"));
        
        DocumentBuilder bld = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        
        Document doc = bld.parse(export);
        
        Element root = doc.getDocumentElement();
        
        assertEquals(ExportingInputStream.CONFIGURATION_EXPORT_ELEMENT, root.getNodeName());
        
        assertEquals(root.getChildNodes().getLength(), 2, "Unexpected number of entities elements");
        
        Element export1 = (Element) root.getChildNodes().item(0);
        Element export2 = (Element) root.getChildNodes().item(1);
        
        assertEquals(export1.getAttribute("id"), StringListSynchronizer.class.getName());
        assertEquals(export2.getAttribute("id"), IntegerListSynchronizer.class.getName());

        String[] expectedNotes = new String[] {list1.toString(), list2.toString()};
        
        for(int i = 0; i < root.getChildNodes().getLength(); ++i) {
            Element entitiesElement = (Element) root.getChildNodes().item(i);
            
            assertEquals(entitiesElement.getNodeName(), ExportingInputStream.ENTITIES_EXPORT_ELEMENT);
            
            NodeList errorMessages = entitiesElement.getElementsByTagName(ExportingInputStream.ERROR_MESSAGE_ELEMENT);
            assertEquals(errorMessages.getLength(), 0, "Unexpected number of error message elements in an entities export.");
            
            Node note = getDirectChildByTagName(entitiesElement, ExportingInputStream.NOTES_ELEMENT);
            
            assertNotNull(note, "Couldn't find exporter notes.");
            
            String notesText = ((Element)note).getTextContent();
            assertEquals(notesText, expectedNotes[i], "Unexpected notes for entities.");
            
            NodeList entityElements = entitiesElement.getElementsByTagName(ExportingInputStream.ENTITY_EXPORT_ELEMENT);
            
            assertEquals(entityElements.getLength(), 3, "Unexpected number of exported entities.");
            
            for(int j = 0; j < entityElements.getLength(); ++j) {
                Element entityElement = (Element) entityElements.item(j);
                
                errorMessages = entityElement.getElementsByTagName(ExportingInputStream.ERROR_MESSAGE_ELEMENT);
                assertEquals(errorMessages.getLength(), 0, "Unexpected number of error message elements in an entity.");
                
                note = getDirectChildByTagName(entityElement, ExportingInputStream.NOTES_ELEMENT);
                assertNotNull(note, "Could not find notes for an exported entity.");
                
                Node data = getDirectChildByTagName(entityElement, ExportingInputStream.DATA_ELEMENT);
                assertNotNull(data, "Could not find data element in the entity.");
                
                String dataText = ((Element)data).getTextContent();                
                notesText = ((Element)note).getTextContent();
                 
                assertEquals(notesText, ListToStringExporter.NOTE_PREFIX + dataText, "Unexpected discrepancy between data and notes in the export.");
            }
        }
    }
    
    private <T> LinkedHashSet<T> asSet(T... ts) {
        LinkedHashSet<T> ret = new LinkedHashSet<T>();
        for (T t : ts) {
            ret.add(t);
        }
        
        return ret;
    }
    
    private static String readAll(Reader rdr) throws IOException {
        try {
            StringBuilder bld = new StringBuilder();
            int c;
            while((c = rdr.read()) != -1) {
                bld.append((char) c);
            }
            
            return bld.toString();
        } finally {
            rdr.close();
        }
    }
    
    private static Node getDirectChildByTagName(Node node, String tagName) {
        for(int i = 0; i < node.getChildNodes().getLength(); ++i) {
            Node n = node.getChildNodes().item(i);
            if (n.getNodeName().equals(tagName)) {
                return n;
            }
        }
        
        return null;
    }
}