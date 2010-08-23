package org.rhq.enterprise.gui.coregui.client.util.selenium;

import com.smartgwt.client.widgets.tree.TreeGrid;

/**
 * Wrapper for com.smartgwt.client.widgets.tree.TreeGrid that sets the ID for use with selenium scLocators.
 * 
 * @author jay shaughnessy
 */
public class LocatableTreeGrid extends TreeGrid implements Locatable {

    private String locatorId;

    /**
     * <pre>
     * ID Format: "scClassname-locatorId"
     * </pre>
     * @param locatorId not null.
     */
    public LocatableTreeGrid(String locatorId) {
        super();
        this.locatorId = locatorId;
        String unsafeId = this.getScClassName() + "-" + locatorId;
        setID(SeleniumUtility.getSafeId(unsafeId));
    }

    public String getLocatorId() {
        return locatorId;
    }

    public String extendLocatorId(String extension) {
        return this.locatorId + "-" + extension;
    }
}