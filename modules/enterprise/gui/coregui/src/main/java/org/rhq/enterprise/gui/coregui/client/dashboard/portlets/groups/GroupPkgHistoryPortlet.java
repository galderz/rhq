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
package org.rhq.enterprise.gui.coregui.client.dashboard.portlets.groups;

import java.util.ArrayList;
import java.util.List;

import java.util.logging.Logger;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.smartgwt.client.widgets.Canvas;
import com.smartgwt.client.widgets.HTMLFlow;
import com.smartgwt.client.widgets.form.DynamicForm;
import com.smartgwt.client.widgets.form.events.SubmitValuesEvent;
import com.smartgwt.client.widgets.form.events.SubmitValuesHandler;
import com.smartgwt.client.widgets.form.fields.SelectItem;
import com.smartgwt.client.widgets.form.fields.StaticTextItem;
import com.smartgwt.client.widgets.layout.VLayout;

import org.rhq.core.domain.common.EntityContext;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.domain.content.InstalledPackageHistory;
import org.rhq.core.domain.criteria.InstalledPackageHistoryCriteria;
import org.rhq.core.domain.dashboard.DashboardPortlet;
import org.rhq.core.domain.util.PageControl;
import org.rhq.core.domain.util.PageList;
import org.rhq.core.domain.util.PageOrdering;
import org.rhq.enterprise.gui.coregui.client.dashboard.AutoRefreshPortlet;
import org.rhq.enterprise.gui.coregui.client.dashboard.AutoRefreshPortletUtil;
import org.rhq.enterprise.gui.coregui.client.dashboard.CustomSettingsPortlet;
import org.rhq.enterprise.gui.coregui.client.dashboard.Portlet;
import org.rhq.enterprise.gui.coregui.client.dashboard.PortletViewFactory;
import org.rhq.enterprise.gui.coregui.client.dashboard.PortletWindow;
import org.rhq.enterprise.gui.coregui.client.dashboard.portlets.PortletConfigurationEditorComponent;
import org.rhq.enterprise.gui.coregui.client.dashboard.portlets.PortletConfigurationEditorComponent.Constant;
import org.rhq.enterprise.gui.coregui.client.gwt.GWTServiceLookup;
import org.rhq.enterprise.gui.coregui.client.inventory.common.detail.summary.AbstractActivityView;
import org.rhq.enterprise.gui.coregui.client.util.GwtRelativeDurationConverter;
import org.rhq.enterprise.gui.coregui.client.util.Log;
import org.rhq.enterprise.gui.coregui.client.util.selenium.LocatableCanvas;
import org.rhq.enterprise.gui.coregui.client.util.selenium.LocatableDynamicForm;
import org.rhq.enterprise.gui.coregui.client.util.selenium.LocatableVLayout;

/**This portlet allows the end user to customize the Package History display
 *
 * @author Simeon Pinder
 */
public class GroupPkgHistoryPortlet extends LocatableVLayout implements CustomSettingsPortlet, AutoRefreshPortlet {

    // A non-displayed, persisted identifier for the portlet
    public static final String KEY = "GroupPackageHistory";
    // A default displayed, persisted name for the portlet
    public static final String NAME = MSG.view_portlet_defaultName_group_pkg_hisory();

    private int groupId = -1;
    protected LocatableCanvas recentPkgHistoryContent = new LocatableCanvas(extendLocatorId("RecentPkgHistory"));
    protected boolean currentlyLoading = false;

    public static final String ID = "id";

    // set on initial configuration, the window for this portlet view.
    protected PortletWindow portletWindow;
    //instance ui widgets

    protected Timer refreshTimer;

    protected static List<String> CONFIG_INCLUDE = new ArrayList<String>();
    static {
        CONFIG_INCLUDE.add(Constant.RESULT_COUNT);
    }

    public GroupPkgHistoryPortlet(String locatorId, int groupId) {
        super(locatorId);
        this.groupId = groupId;
    }

    @Override
    protected void onInit() {
        initializeUi();
        loadData();
    }

    /**Defines layout for the portlet page.
     */
    protected void initializeUi() {
        setPadding(5);
        setMembersMargin(5);
        addMember(recentPkgHistoryContent);
    }

    /** Responsible for initialization and lazy configuration of the portlet values
     */
    public void configure(PortletWindow portletWindow, DashboardPortlet storedPortlet) {
        //populate portlet configuration details
        if (null == this.portletWindow && null != portletWindow) {
            this.portletWindow = portletWindow;
        }

        if ((null == storedPortlet) || (null == storedPortlet.getConfiguration())) {
            return;
        }

        Configuration portletConfig = storedPortlet.getConfiguration();

        //lazy init any elements not yet configured.
        for (String key : PortletConfigurationEditorComponent.CONFIG_PROPERTY_INITIALIZATION.keySet()) {
            if ((portletConfig.getSimple(key) == null) && CONFIG_INCLUDE.contains(key)) {
                portletConfig.put(new PropertySimple(key,
                    PortletConfigurationEditorComponent.CONFIG_PROPERTY_INITIALIZATION.get(key)));
            }
        }
    }

    public Canvas getHelpCanvas() {
        return new HTMLFlow(MSG.view_portlet_help_pkg_history());
    }

    public static final class Factory implements PortletViewFactory {
        public static PortletViewFactory INSTANCE = new Factory();

        public final Portlet getInstance(String locatorId, EntityContext context) {

            if (EntityContext.Type.ResourceGroup != context.getType()) {
                throw new IllegalArgumentException("Context [" + context + "] not supported by portlet");
            }

            return new GroupPkgHistoryPortlet(locatorId, context.getGroupId());
        }
    }

    protected void loadData() {
        currentlyLoading = true;
        getRecentPkgHistory();
    }

    @Override
    public DynamicForm getCustomSettingsForm() {
        final DashboardPortlet storedPortlet = this.portletWindow.getStoredPortlet();
        final Configuration portletConfig = storedPortlet.getConfiguration();

        LocatableDynamicForm customSettings = new LocatableDynamicForm(extendLocatorId("customSettings"));
        LocatableVLayout page = new LocatableVLayout(customSettings.extendLocatorId("page"));
        //build editor form container
        final LocatableDynamicForm form = new LocatableDynamicForm(page.extendLocatorId("pkg-history"));
        form.setMargin(5);
        //add result count selector
        final SelectItem resultCountSelector = PortletConfigurationEditorComponent.getResultCountEditor(portletConfig);
        form.setItems(resultCountSelector);

        //submit handler
        customSettings.addSubmitValuesHandler(new SubmitValuesHandler() {

            @Override
            public void onSubmitValues(SubmitValuesEvent event) {

                //results count
                Configuration updatedConfig = AbstractActivityView.saveResultCounterSettings(resultCountSelector,
                    portletConfig);

                //persist
                storedPortlet.setConfiguration(updatedConfig);
                configure(portletWindow, storedPortlet);
                refresh();
            }

        });
        page.addMember(form);
        customSettings.addChild(page);
        return customSettings;
    }

    /** Fetches recent package history information and updates the DynamicForm instance with details.
     */
    protected void getRecentPkgHistory() {
        final DashboardPortlet storedPortlet = this.portletWindow.getStoredPortlet();
        final Configuration portletConfig = storedPortlet.getConfiguration();
        final int groupId = this.groupId;
        InstalledPackageHistoryCriteria criteria = new InstalledPackageHistoryCriteria();

        int resultCount = 5;//default to
        //result count
        PropertySimple property = portletConfig.getSimple(Constant.RESULT_COUNT);
        if (property != null) {
            String currentSetting = property.getStringValue();
            if (currentSetting.trim().isEmpty() || currentSetting.equalsIgnoreCase("5")) {
                resultCount = 5;
            } else {
                resultCount = Integer.valueOf(currentSetting);
            }
        }
        PageControl pageControl = new PageControl(0, resultCount);
        criteria.setPageControl(pageControl);
        criteria.addFilterResourceGroupIds(groupId);

        criteria.addSortStatus(PageOrdering.DESC);

        GWTServiceLookup.getContentService().findInstalledPackageHistoryByCriteria(criteria,

        new AsyncCallback<PageList<InstalledPackageHistory>>() {
            @Override
            public void onFailure(Throwable caught) {
                Log.debug("Error retrieving installed package history for group [" + groupId + "]:"
                        + caught.getMessage());
                currentlyLoading = false;
            }

            @Override
            public void onSuccess(PageList<InstalledPackageHistory> result) {
                VLayout column = new VLayout();
                column.setHeight(10);
                if (!result.isEmpty()) {
                    for (InstalledPackageHistory history : result) {
                        LocatableDynamicForm row = new LocatableDynamicForm(recentPkgHistoryContent
                            .extendLocatorId(history.getPackageVersion().getFileName()
                                + history.getPackageVersion().getVersion()));
                        row.setNumCols(3);

                        StaticTextItem iconItem = AbstractActivityView.newTextItemIcon(
                            "subsystems/content/Package_16.png", null);
                        String title = history.getPackageVersion().getFileName() + ":";
                        //String destination = "/rhq/resource/content/audit-trail-item.xhtml?id=" + groupId
                        //   + "&selectedHistoryId=" + history.getId();
                        //spinder 4/27/11: diabling links as they point into portal.war content pages
                        //                        LinkItem link = AbstractActivityView.newLinkItem(title, destination);
                        StaticTextItem link = AbstractActivityView.newTextItem(title);
                        StaticTextItem time = AbstractActivityView.newTextItem(GwtRelativeDurationConverter
                            .format(history.getTimestamp()));

                        row.setItems(iconItem, link, time);
                        column.addMember(row);
                    }
                    //                    //insert see more link
                    //                    LocatableDynamicForm row = new LocatableDynamicForm(recentPkgHistoryContent
                    //                        .extendLocatorId("PkgHistoryContentSeeMore"));
                    //                    String destination = "/rhq/resource/content/audit-trail-item.xhtml?id=" + groupId;
                    //                    addSeeMoreLink(row, destination, column);
                } else {
                    LocatableDynamicForm row = AbstractActivityView.createEmptyDisplayRow(recentPkgHistoryContent
                        .extendLocatorId("None"), MSG.view_portlet_results_empty());
                    column.addMember(row);
                }
                //cleanup
                for (Canvas child : recentPkgHistoryContent.getChildren()) {
                    child.destroy();
                }
                recentPkgHistoryContent.addChild(column);
                recentPkgHistoryContent.markForRedraw();
                currentlyLoading = false;
                markForRedraw();
            }
        });
    }

    public void startRefreshCycle() {
        refreshTimer = AutoRefreshPortletUtil.startRefreshCycle(this, this, refreshTimer);
    }

    @Override
    protected void onDestroy() {
        AutoRefreshPortletUtil.onDestroy(this, refreshTimer);

        super.onDestroy();
    }

    public boolean isRefreshing() {
        return this.currentlyLoading;
    }

    @Override
    public void refresh() {
        if (!isRefreshing()) {
            loadData();
        }
    }
}