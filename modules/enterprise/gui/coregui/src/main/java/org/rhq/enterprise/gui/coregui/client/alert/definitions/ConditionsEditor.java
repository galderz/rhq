/*
 * RHQ Management Platform
 * Copyright (C) 2005-2010 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2, as
 * published by the Free Software Foundation, and/or the GNU Lesser
 * General Public License, version 2.1, also as published by the Free
 * Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License and the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License
 * and the GNU Lesser General Public License along with this program;
 * if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.rhq.enterprise.gui.coregui.client.alert.definitions;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.smartgwt.client.data.DSRequest;
import com.smartgwt.client.data.DSResponse;
import com.smartgwt.client.data.DataSourceField;
import com.smartgwt.client.data.Record;
import com.smartgwt.client.data.fields.DataSourceTextField;
import com.smartgwt.client.types.Overflow;
import com.smartgwt.client.widgets.Window;
import com.smartgwt.client.widgets.events.CloseClickHandler;
import com.smartgwt.client.widgets.events.CloseClientEvent;
import com.smartgwt.client.widgets.grid.ListGridRecord;

import org.rhq.core.domain.alert.AlertCondition;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.enterprise.gui.coregui.client.alert.AlertFormatUtility;
import org.rhq.enterprise.gui.coregui.client.components.table.AbstractTableAction;
import org.rhq.enterprise.gui.coregui.client.components.table.Table;
import org.rhq.enterprise.gui.coregui.client.components.table.TableActionEnablement;
import org.rhq.enterprise.gui.coregui.client.util.RPCDataSource;
import org.rhq.enterprise.gui.coregui.client.util.selenium.LocatableVLayout;
import org.rhq.enterprise.gui.coregui.client.util.selenium.LocatableWindow;

/**
 * @author John Mazzitelli
 */
public class ConditionsEditor extends LocatableVLayout {

    private ResourceType resourceType;
    private HashSet<AlertCondition> conditions;
    private Table table;

    public ConditionsEditor(String locatorId, ResourceType resourceType, HashSet<AlertCondition> conditions) {
        super(locatorId);
        this.resourceType = resourceType;
        setConditions(conditions);
    }

    /**
     * Returns the conditions that this editor currently has in memory.
     * This will never be <code>null</code>.
     * 
     * @return conditions set that was possibly edited by the user
     */
    public HashSet<AlertCondition> getConditions() {
        return conditions;
    }

    public void setConditions(Set<AlertCondition> set) {
        conditions = new HashSet<AlertCondition>(); // make our own copy
        if (set != null) {
            conditions.addAll(set);
        }
        if (table != null) {
            table.refresh();
        }
    }

    @Override
    protected void onInit() {
        super.onInit();

        table = new ConditionsTable(extendLocatorId("conditionsTable"));

        addMember(table);
    }

    public void setEditable(boolean editable) {
        table.setTableActionDisableOverride(!editable);
    }

    private class ConditionsTable extends Table<ConditionDataSource> {
        private ConditionsTable(String locatorId) {
            super(locatorId);

            setShowHeader(false);

            final ConditionDataSource dataSource = new ConditionDataSource();
            setDataSource(dataSource);
        }

        @Override
        protected void configureTable() {
            addTableAction(this.extendLocatorId("add"), "Add", null, new AbstractTableAction() {
                public void executeAction(ListGridRecord[] selection, Object actionValue) {
                    final Window winModal = new LocatableWindow(ConditionsEditor.this
                        .extendLocatorId("newConditionEditorWindow"));
                    winModal.setTitle(MSG.view_alert_common_tab_conditions_modal_title());
                    winModal.setOverflow(Overflow.VISIBLE);
                    winModal.setShowMinimizeButton(false);
                    winModal.setIsModal(true);
                    winModal.setShowModalMask(true);
                    winModal.setAutoSize(true);
                    winModal.setAutoCenter(true);
                    //winModal.setShowResizer(true);
                    //winModal.setCanDragResize(true);
                    winModal.centerInPage();
                    winModal.addCloseClickHandler(new CloseClickHandler() {
                        @Override
                        public void onCloseClick(CloseClientEvent event) {
                            winModal.markForDestroy();
                        }
                    });

                    NewConditionEditor newConditionEditor = new NewConditionEditor(extendLocatorId("newConditionEditor"),
                        conditions, ConditionsEditor.this.resourceType, new Runnable() {
                            @Override
                            public void run() {
                                winModal.markForDestroy();
                                refresh();
                            }
                        });
                    winModal.addItem(newConditionEditor);
                    winModal.show();
                }
        });

        table.addTableAction(this.extendLocatorId("delete"), MSG.common_button_delete(), MSG
            .view_alert_definitions_delete_confirm(), new AbstractTableAction(TableActionEnablement.ANY) {
            public void executeAction(ListGridRecord[] selection, Object actionValue) {
                for (ListGridRecord record : selection) {
                    AlertCondition cond = getDataSource().copyValues(record);
                    conditions.remove(cond);
                }
                refresh();
            }
        });

        }
    }

    private class ConditionDataSource extends RPCDataSource<AlertCondition> {
        private static final String FIELD_OBJECT = "obj";
        private static final String FIELD_CONDITION = "condition";

        public ConditionDataSource() {
            super();
            List<DataSourceField> fields = addDataSourceFields();
            addFields(fields);
        }

        @Override
        protected List<DataSourceField> addDataSourceFields() {
            List<DataSourceField> fields = super.addDataSourceFields();

            DataSourceTextField conditionField = new DataSourceTextField(FIELD_CONDITION, MSG
                .view_alert_common_tab_conditions_text());
            fields.add(conditionField);

            return fields;
        }

        @Override
        public AlertCondition copyValues(Record from) {
            return (AlertCondition) from.getAttributeAsObject(FIELD_OBJECT);
        }

        @Override
        public ListGridRecord copyValues(AlertCondition from) {
            ListGridRecord record = new ListGridRecord();
            record.setAttribute(FIELD_CONDITION, AlertFormatUtility.formatAlertConditionForDisplay(from));
            record.setAttribute(FIELD_OBJECT, from);
            return record;
        }

        @Override
        protected void executeFetch(DSRequest request, DSResponse response) {
            response.setData(buildRecords(conditions));
            processResponse(request.getRequestId(), response);
        }
    }
}