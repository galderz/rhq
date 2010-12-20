/*
 * RHQ Management Platform
 * Copyright (C) 2005-2010 Red Hat, Inc.
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
package org.rhq.enterprise.gui.coregui.client.gwt;

import com.google.gwt.user.client.rpc.RemoteService;

import org.rhq.core.domain.alert.AlertDefinition;
import org.rhq.core.domain.alert.notification.AlertNotification;
import org.rhq.core.domain.configuration.definition.ConfigurationDefinition;
import org.rhq.core.domain.criteria.AlertDefinitionCriteria;
import org.rhq.core.domain.util.PageList;

public interface AlertDefinitionGWTService extends RemoteService {

    PageList<AlertDefinition> findAlertDefinitionsByCriteria(AlertDefinitionCriteria criteria);

    int createAlertDefinition(AlertDefinition alertDefinition, Integer resourceId) throws Exception;

    AlertDefinition updateAlertDefinition(int alertDefinitionId, AlertDefinition alertDefinition,
        boolean updateInternals) throws Exception;

    int enableAlertDefinitions(int[] alertDefinitionIds) throws Exception;

    int disableAlertDefinitions(int[] alertDefinitionIds) throws Exception;

    int removeAlertDefinitions(int[] alertDefinitionIds) throws Exception;

    String[] getAlertNotificationConfigurationPreview(AlertNotification[] notifs) throws Exception;

    String[] getAllAlertSenders() throws Exception;

    ConfigurationDefinition getConfigurationDefinitionForSender(String sender) throws Exception;
}