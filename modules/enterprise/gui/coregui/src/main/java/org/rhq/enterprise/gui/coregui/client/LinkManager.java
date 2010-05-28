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
package org.rhq.enterprise.gui.coregui.client;

/**
 * @author Greg Hinkle
 */
public class LinkManager {

    private static boolean GWT = true;


    public static String getResourceLink(int resourceId) {

        if (GWT) {
            return "Resource/" + resourceId;
        } else {
            return "/rhq/resource/summary/overview.xhtml?id=" + resourceId;
        }
    }


    public static String getResourceGroupLink(int resouceGroupId) {

        if (GWT) {
            return "ResourceGroup/" + resouceGroupId;
        } else {
            return "/rhq/group/inventory/view.xhtml?groupId=" + resouceGroupId;
        }
    }

    public static String getSubsystemConfigurationLink() {
        return "/rhq/subsystem/configurationUpdate.xhtml";
    }

    public static String getSubsystemSuspectMetricsLink() {
        return "/rhq/subsystem/oobHistory.xhtml";
    }

    public static String getSubsystemOperationHistoryLink() {
        return "/rhq/subsystem/operationHistory.xhtml";
    }


    public static String getSubsystemAlertHistoryLink() {
        return "/rhq/subsystem/alertHistory.xhtml";
    }

    public static String getSubsystemAlertDefsLink() {
        return "/rhq/subsystem/alertDefinitions.xhtml";
    }


    public static String getAutodiscoveryQueueLink() {
        return "/rhq/discovery/queue.xhtml";
    }

    public static String getDashboardLink() {
        return "/Dashboard.do";
    }


    public static String getHubAllResourcesLink() {
        return "/rhq/inventory/browseResources.xhtml?subtab=all";
    }

    public static String getHubPlatformsLink() {
        return "/rhq/inventory/browseResources.xhtml?subtab=platform";
    }

    public static String getHubServerssLink() {
        return "/rhq/inventory/browseResources.xhtml?subtab=server";
    }

    public static String getHubServicesLink() {
        return "/rhq/inventory/browseResources.xhtml?subtab=service";
    }


    public static String getSavedSearchLink(int searchId) {
        return "/rhq/inventory/browseResources.xhtml?subtab=all&amp;searchId=" + searchId;
    }


    public static String getHubAllGroupsLink() {
        return "/rhq/inventory/browseGroups.xhtml?subtab=all";
    }

    public static String getHubCompatibleGroupsLink() {
        return "/rhq/inventory/browseGroups.xhtml?subtab=compatible";
    }

    public static String getHubMixedGroupsLink() {
        return "/rhq/inventory/browseGroups.xhtml?subtab=mixed";
    }

    public static String getHubGroupDefinitionsLink() {
        return "/rhq/definition/group/list.xhtml";
    }

    public static String getHubNewGroupLink() {
        return "/resource/group/Inventory.do?mode=new";
    }


    public static String getHubNewGroupDefLink() {
        return "/rhq/definition/group/new.xhtml";
    }


    public static String getAdminUsersLink() {
        return "/admin/user/UserAdmin.do?mode=list";
    }

    public static String getAdminRolesLink() {
        return "/admin/role/RoleAdmin.do?mode=list";
    }

    public static String getAdminSysConfigLink() {
        return "/admin/config/Config.do?mode=edit";
    }


    public static String getAdminPluginsLink() {
        return "/rhq/admin/plugin/plugin-list.xhtml";
    }


    public static String getAdminTemplatesLink() {
        return "/admin/config/EditDefaults.do?mode=monitor&amp;viewMode=all";
    }

    public static String getAdminAlertNotifTemplatesLink() {
        return "/rhq/admin/alert/template/notification/list.xhtml";
    }


    public static String getAdminContentProvidersLink() {
        return "/rhq/content/listContentProviders.xhtml";
    }

    public static String getAdminContentReposLink() {
        return "/rhq/content/listRepos.xhtml";
    }


    public static String getHAServersLink() {
        return "/rhq/ha/listServers.xhtml";
    }

    public static String getHAAgentsLink() {
        return "/rhq/ha/listAgents.xhtml";
    }

    public static String getHAAffinityGroupsLink() {
        return "/rhq/ha/listAffinityGroups.xhtml";
    }

    public static String getHAEventsLink() {
        return "/rhq/ha/listPartitionEvents.xhtml";
    }


    public static String getReportsInventoryLink() {
        return "/rhq/admin/report/resourceInstallReport.xhtml";
    }

    public static String getAdminDownloadsLink() {
        return "/rhq/admin/downloads.xhtml";
    }


    public static String getAdminLicenseLink() {
        return "/admin/license/LicenseAdmin.do?mode=view";
    }


    public static String getDebugSqlLink() {
        return "/admin/test/sql.jsp";
    }

    public static String getDebugHibernateLink() {
        return "/admin/test/hibernate.jsp";
    }

    public static String getDebugBrowserLink() {
        return "/admin/test/browser.jsp";
    }

    public static String getUserPrefsLink(int subjectId) {
        return "/admin/user/UserAdmin.do?mode=edit&amp;u=" + subjectId;
    }

    public static String getUserPasswordLink(int subjectId) {
        return "/admin/user/UserAdmin.do?mode=editPass&amp;u=" + subjectId;
    }


}