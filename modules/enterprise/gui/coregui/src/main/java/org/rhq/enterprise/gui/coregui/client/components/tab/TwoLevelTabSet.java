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
package org.rhq.enterprise.gui.coregui.client.components.tab;

import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.History;
import com.smartgwt.client.widgets.Canvas;
import com.smartgwt.client.widgets.tab.Tab;
import com.smartgwt.client.widgets.tab.events.TabSelectedEvent;
import com.smartgwt.client.widgets.tab.events.TabSelectedHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A tab set where each {@link TwoLevelTab tab} has one or more {@link SubTab subtab}s.
 *
 * @author Jay Shaughnessy
 * @author John Sanda
 */
public class TwoLevelTabSet extends NamedTabSet implements TabSelectedHandler, TwoLevelTabSelectedHandler {

    private static class TabPanePair {
        public TwoLevelTab tab;
        public Canvas pane;

        public TabPanePair(TwoLevelTab tab, Canvas pane) {
            this.tab= tab;
            this.pane = pane;
        }
    }

    /** maps Tab locator IDs to Tabs and their associated panes. */
    private Map<String, TabPanePair> hiddenTabs = new LinkedHashMap<String, TabPanePair>();

    private boolean ignoreSelectEvents = false;

    public TwoLevelTabSet(String locatorId) {
        super(locatorId);
    }

    public void setTabs(TwoLevelTab... tabs) {
        super.setTabs(tabs);
        for (TwoLevelTab tab : tabs) {
            tab.getLayout().addTwoLevelTabSelectedHandler(this);
            updateTab(tab, tab.getPane());
        }

        addTabSelectedHandler(this);
    }

    public TwoLevelTab[] getTabs() {
        Tab[] tabs = super.getTabs();
        TwoLevelTab[] twoLevelTabs = new TwoLevelTab[tabs.length];
        for (int i = 0, tabsLength = tabs.length; i < tabsLength; i++) {
            Tab tab = tabs[i];
            if (!(tab instanceof TwoLevelTab)) {
                throw new IllegalStateException("TwoLevelTabSet contains a Tab that is not a TwoLevelTab.");
            }
            twoLevelTabs[i] = (TwoLevelTab) tab;
        }
        return twoLevelTabs;
    }

    // Smartgwt does not currently offer the ability to hide a Tab (why!) so we fake it here.  This allows us to keep
    // the Tab structure in place while removing it from the TabSet
    public void setTabHidden(TwoLevelTab tab, boolean hidden) {
        if (hidden) {
            if (hiddenTabs.containsKey(tab.getLocatorId())) {
                return;
            }

            // don't let the removeTab() destroy the pane
            Canvas contentPane = tab.getPane();
            updateTab(tab, null);
            removeTab(tab);

            // Previously we called tab.setPane(contentPane) here to reset the
            // pane. With the upgrade to SmartGWT 3.0 however, resetting the
            // pane prevents the tab from being hidden. This issue was logged
            // under https://bugzilla.redhat.com/show_bug.cgi?id=772259. Note
            // that we need to hold onto both the tab and its pane so that the
            // tab content can be displayed when the tab is made visible again.
            // We hold onto the tab and its pane in a TabPanePair.
            hiddenTabs.put(tab.getLocatorId(), new TabPanePair(tab, contentPane));
        } else {
            if (!hiddenTabs.containsKey(tab.getLocatorId())) {
                return;
            }

            TabPanePair pair = hiddenTabs.remove(tab.getLocatorId());
            tab.setPane(pair.pane);
            addTab(tab);
        }
    }

    public void destroyViews() {
        for (TwoLevelTab tab : getTabs()) {
            tab.getLayout().destroyViews();
        }
        for (TabPanePair pair : hiddenTabs.values()) {
            pair.tab.getLayout().destroyViews();
        }
    }

    // ------- Event support -------
    // Done with a separate handler manager from parent class on purpose (compatibility issue)

    private HandlerManager m = new HandlerManager(this);

    public HandlerRegistration addTwoLevelTabSelectedHandler(TwoLevelTabSelectedHandler handler) {
        return m.addHandler(TwoLevelTabSelectedEvent.TYPE, handler);
    }

    // This is invoked by smartgwt when the user clicks on a Tab in the TabSet, or TabSet.selectTab() is called. It
    // sets the current SubTab and fires an event to notify AbstractTwoLevelTabSet that a tab/subtab has been selected.
    public void onTabSelected(TabSelectedEvent tabSelectedEvent) {
        // if requested, ignore select tab notifications. smartgwt can generate unwanted notifications
        // while we manipulate the tabset (e.g. when hiding the current tab). We want to manage this at a higher level
        if (isIgnoreSelectEvents()) {
            return;
        }

        TwoLevelTab tab = (TwoLevelTab) getSelectedTab();
        SubTab currentSubTab = tab.getLayout().getCurrentSubTab();
        if (null != currentSubTab) {
            TwoLevelTabSelectedEvent event = new TwoLevelTabSelectedEvent(tab.getName(), tab.getLayout()
                .getCurrentSubTab().getName(), tabSelectedEvent.getTabNum(), tab.getLayout().getCurrentCanvas(),
                History.getToken());
            m.fireEvent(event);
        }
    }

    // This is invoked by an event fired in SubTabLayout when the user clicks a SubTab button. It sets the Tab
    // and fires an event to notify AbstractTwoLevelTabSet that a tab/subtab has been selected.
    public void onTabSelected(TwoLevelTabSelectedEvent tabSelectedEvent) {
        // if requested, ignore select tab notifications. smartgwt can generate unwanted notifications
        // while we manipulate the tabset (e.g. when hiding the current tab). We want to manage this at a higher level
        if (isIgnoreSelectEvents()) {
            return;
        }

        tabSelectedEvent.setTabNum(getSelectedTabNumber());
        Tab tab = getSelectedTab();
        tabSelectedEvent.setId(this.getTabByTitle(tab.getTitle()).getName());
        m.fireEvent(tabSelectedEvent);
    }

    public TwoLevelTab getDefaultTab() {
        TwoLevelTab[] tabs = getTabs();
        for (TwoLevelTab tab : tabs) {
            if (!tab.getDisabled()) {
                return tab;
            }
        }
        return null;
    }

    public TwoLevelTab getTabByName(String name) {
        return (TwoLevelTab) super.getTabByName(name);
    }

    public TwoLevelTab getTabByTitle(String title) {
        return (TwoLevelTab) super.getTabByTitle(title);
    }

    public TwoLevelTab getTabByLocatorId(String locatorId) {
        return (TwoLevelTab) super.getTabByLocatorId(locatorId);
    }

    public void setTabEnabled(TwoLevelTab tab, boolean enabled) {
        if (enabled) {
            enableTab(tab);
        } else {
            disableTab(tab);
        }
    }

    public boolean isIgnoreSelectEvents() {
        return ignoreSelectEvents;
    }

    public void setIgnoreSelectEvents(boolean ignoreSelectEvents) {
        this.ignoreSelectEvents = ignoreSelectEvents;
    }

    @Override
    public void destroy() {
        // add the hidden tabs back under the TabSet. This will get them destroyed by smartgwt when the tabset
        // goes away. There is no explicit Tab.destroy().
        for (TabPanePair pair : hiddenTabs.values()) {
            Tab tab = pair.tab;
            tab.setPane(pair.pane);
            addTab(tab);
        }
        for (TwoLevelTab tab : getTabs()) {
            tab.getLayout().destroyViews();
        }
        super.destroy();
    }

}
