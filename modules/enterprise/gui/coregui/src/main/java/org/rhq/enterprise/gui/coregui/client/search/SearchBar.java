/*
 * RHQ Management Platform
 * Copyright (C) 2010 Red Hat, Inc.
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
package org.rhq.enterprise.gui.coregui.client.search;

import java.util.List;

import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;

import org.rhq.core.domain.criteria.SavedSearchCriteria;
import org.rhq.core.domain.search.SavedSearch;
import org.rhq.enterprise.gui.coregui.client.gwt.GWTServiceLookup;
import org.rhq.enterprise.gui.coregui.client.gwt.SearchGWTServiceAsync;
import org.rhq.enterprise.gui.coregui.client.search.favorites.SavedSearchGrid;
import org.rhq.enterprise.gui.coregui.client.search.favorites.SavedSearchManager;
import org.rhq.enterprise.gui.coregui.client.search.favorites.SavedSearchGrid.PatternSelectionHandler;
import org.rhq.enterprise.gui.coregui.client.search.suggest.SuggestTextBox;

/**
 * @author Joseph Marques
 */
public class SearchBar {

    public static final String DEFAULT_SEARCH_TEXT = "search for resources";
    public static final String DEFAULT_PATTERN_NAME = "name your pattern";

    private static final String IMAGE_DIR = "/coregui/images/search/";

    private static final String STAR_OFF_URL = IMAGE_DIR + "star1.png";
    private static final String STAR_ACTIVE_URL = IMAGE_DIR + "star2.png";
    private static final String STAR_ON_URL = IMAGE_DIR + "star3.png";

    private static final String ARROW_WHITE_URL = IMAGE_DIR + "menu_arrow.png";
    private static final String ARROW_GRAY_URL = IMAGE_DIR + "menu_arrow_down.png";

    public static final String TRASH = IMAGE_DIR + "trash.png";

    private final SuggestTextBox autoCompletePatternField;
    private final TextBox patternNameField = new TextBox();
    private final Label patternNameLabel = new Label();

    private final Image starImage = new Image(STAR_OFF_URL);
    private final Image arrowImage = new Image(ARROW_WHITE_URL);

    private final PopupPanel savedSearchesPanel = new PopupPanel(true);
    private final SavedSearchGrid savedSearches = new SavedSearchGrid();

    private String currentSearch = "";
    private long lastNameFieldBlurTime = 0;

    private final SavedSearchManager savedSearchManager = SavedSearchManager.get();
    private final SearchGWTServiceAsync searchService = GWTServiceLookup.getSearchService();

    public static SearchBar singleton;

    public static SearchBar get() {
        if (singleton == null) {
            singleton = new SearchBar();
        }
        return singleton;
    }

    public static boolean existsOnPage() {
        return DOM.getElementById("searchBar") != null;
    }

    private SearchBar() {
        System.out.println("Loading SearchBar...");

        autoCompletePatternField = new SuggestTextBox("patternFieldSuggestionsContainer");

        RootPanel.get("patternFieldContainer").add(autoCompletePatternField);
        RootPanel.get("patternNameFieldContainer").add(patternNameField);
        RootPanel.get("patternNameLabelContainer").add(patternNameLabel);
        RootPanel.get("starImageContainer").add(starImage);
        RootPanel.get("arrowImageContainer").add(arrowImage);
        RootPanel.get("savedSearchesContainer").add(savedSearchesPanel);

        setupAutoCompletingPatternField();
        setupPatternNameField();
        setupPatternNameLabel();
        setupStarImage();
        setupArrowImage();
        setupSavedSearches();
        setupSearchButton();

        // presume the enclosing page logic loads results without a button click
    }

    public void executeSearch() {
        String searchTerms = autoCompletePatternField.getText().toLowerCase().trim();
        executeSearch(searchTerms);
    }

    public void executeSearch(String searchTerms) {
        if (searchTerms.equals(DEFAULT_SEARCH_TEXT)) {
            searchTerms = "";
        }

        SavedSearchCriteria criteria = new SavedSearchCriteria();
        criteria.addFilterName(searchTerms);
        criteria.setCaseSensitive(true);

        searchService.findSavedSearchesByCriteria(criteria, new AsyncCallback<List<SavedSearch>>() {

            @Override
            public void onSuccess(List<SavedSearch> result) {
                if (result.size() == 1) {
                    process(result.get(0));
                }

            }

            private void process(SavedSearch savedSearch) {
                autoCompletePatternField.setValue(savedSearch.getPattern(), true);
                patternNameField.setValue(savedSearch.getName(), true);
                currentSearch = "";
                turnNameFieldIntoLabel();
            }

            @Override
            public void onFailure(Throwable caught) {
                // TODO Auto-generated method stub
            }
        });

    }

    private void setupAutoCompletingPatternField() {
        autoCompletePatternField.setText(DEFAULT_SEARCH_TEXT);
        autoCompletePatternField.getElement().setId("patternField");
        autoCompletePatternField.addStyleName("patternField");

        AutoCompletePatternFieldEventHandler handler = new AutoCompletePatternFieldEventHandler();
        autoCompletePatternField.addClickHandler(handler);
        autoCompletePatternField.addKeyPressHandler(handler);
    }

    private void setupPatternNameField() {
        patternNameField.addStyleName("patternNameField");
        patternNameField.setVisible(false);

        PatternNameFieldEventHandler handler = new PatternNameFieldEventHandler();
        patternNameField.addKeyPressHandler(handler);
        patternNameField.addClickHandler(handler);
        patternNameField.addBlurHandler(handler);
    }

    private void setupPatternNameLabel() {
        patternNameLabel.addStyleName("patternNameLabel");
        patternNameLabel.setVisible(false);

        PatternNameLabelEventHandler handler = new PatternNameLabelEventHandler();
        patternNameLabel.addClickHandler(handler);
    }

    private void setupStarImage() {
        StarImageEventHandler handler = new StarImageEventHandler();
        starImage.addClickHandler(handler);
        starImage.addMouseOverHandler(handler);
        starImage.addMouseOutHandler(handler);
    }

    private void setupArrowImage() {
        ArrowImageEventHandler handler = new ArrowImageEventHandler();
        arrowImage.addClickHandler(handler);
    }

    private void setupSavedSearches() {
        savedSearchesPanel.add(savedSearches);
        savedSearchesPanel.removeStyleName("gwt-PopupPanel");
        savedSearchesPanel.addStyleName("savedSearchesPanel");
        savedSearches.addStyleName("savedSearchesPanel");

        // panel position will be re-calculated on down-arrow click
        savedSearchesPanel.show();
        savedSearchesPanel.hide();

        SavedSearchesEventHandler handler = new SavedSearchesEventHandler();
        savedSearchesPanel.addCloseHandler(handler);
        savedSearches.setPatternSelectionHandler(handler);
    }

    private void setupSearchButton() {
        Element searchButtonContainerElement = DOM.getElementById("searchButtonContainer");
        String searchButtonElementId = searchButtonContainerElement.getClassName().split(" ")[0]; // yeah, it's hacky

        Element searchButtonElement = DOM.getElementById(searchButtonElementId);
        if (searchButtonElement == null) {
            return;
        }

        DOM.setEventListener(searchButtonElement, new EventListener() {
            @Override
            public void onBrowserEvent(Event event) {
                executeSearch();
            }
        });

        //searchButton.addStyleName("searchButton");
        //SearchButtonEventHandler handler = new SearchButtonEventHandler();
        //searchButton.addClickHandler(handler);
    }

    private void turnNameFieldIntoLabel() {
        String pattern = autoCompletePatternField.getValue();
        String name = patternNameField.getText();

        if (name.equalsIgnoreCase(DEFAULT_PATTERN_NAME)) {
            name = "";
        }

        savedSearchManager.removePatternByName(currentSearch);

        arrowImage.setVisible(true);
        patternNameField.setVisible(false);

        if (name.equals("")) {
            starImage.setUrl(STAR_OFF_URL);
        } else {
            savedSearchManager.updatePatternByName(name, pattern);
            patternNameLabel.setVisible(true);
            patternNameLabel.setText(elipse(name));
            starImage.setUrl(STAR_ON_URL);
        }
        currentSearch = name;
    }

    private void turnNameLabelIntoField() {
        String name = currentSearch;
        patternNameField.setText(name);
        patternNameField.setVisible(true);
        patternNameLabel.setVisible(false);
        patternNameField.setFocus(true);
    }

    private String elipse(String data) {
        if (data.length() > 14) {
            return data.substring(0, 14) + "...";
        }
        return data;
    }

    /*
     * Event Handlers
     */

    class AutoCompletePatternFieldEventHandler implements KeyPressHandler, ClickHandler {
        @Override
        public void onKeyPress(KeyPressEvent event) {
            // hide pattern field/label, turn off star
            patternNameLabel.setText("");
            patternNameLabel.setVisible(false);
            patternNameField.setValue("", true);
            patternNameField.setVisible(false);
            currentSearch = "";
            starImage.setUrl(STAR_OFF_URL);

            // enter submits a search result under certain conditions, escape hides the suggestions drop-down
            if (event.getCharCode() == KeyCodes.KEY_ENTER) {
                boolean suggestionsHidden = !autoCompletePatternField.isSuggestionListShowing();
                boolean suggestionSelected = !suggestionsHidden && autoCompletePatternField.isItemSelected();
                if (suggestionsHidden || !suggestionSelected) {
                    executeSearch();
                    autoCompletePatternField.hidePopup();
                }
            } else if (event.getCharCode() == KeyCodes.KEY_ESCAPE) {
                autoCompletePatternField.hidePopup();
                event.preventDefault();
                event.stopPropagation();
            }
        }

        @Override
        public void onClick(ClickEvent event) {
            // clear default search text if necessary
            if (autoCompletePatternField.getText().equals(DEFAULT_SEARCH_TEXT)) {
                autoCompletePatternField.setValue("", true);
            }
            savedSearchesPanel.hide();
        }
    }

    class PatternNameFieldEventHandler implements KeyPressHandler, ClickHandler, BlurHandler {
        @Override
        public void onKeyPress(KeyPressEvent event) {
            if (event.getCharCode() == 13) {
                SearchLogger.debug("key press pattern name field");
                turnNameFieldIntoLabel();
            }
        }

        @Override
        public void onClick(ClickEvent event) {
            if (patternNameField.getText().equals(DEFAULT_PATTERN_NAME)) {
                patternNameField.setValue("", false);
            }
        }

        @Override
        public void onBlur(BlurEvent event) {
            lastNameFieldBlurTime = System.currentTimeMillis();
            turnNameFieldIntoLabel();
        }
    }

    class PatternNameLabelEventHandler implements ClickHandler {
        @Override
        public void onClick(ClickEvent event) {
            turnNameLabelIntoField();
        }
    }

    class StarImageEventHandler implements ClickHandler, MouseOverHandler, MouseOutHandler {
        @Override
        public void onClick(ClickEvent event) {
            long diff = System.currentTimeMillis() - lastNameFieldBlurTime;
            if (Math.abs(diff) < 750) {
                /* 
                 * This event propagation is annoying.  If the threshold is set too low, then both
                 * the name field blur event and this star image click event fire...but the blur
                 * event fires first, which turns the star white.  Then a click on a white star
                 * triggers edit mode, re-enabling the name field.  However, setting the threshold
                 * too high will prevent the click event from being handled when the user naturally
                 * wants to click on the star in rapid succession within the threshold time frame.
                 * It is hoped that 750ms will strike a nice balance, and that most users will never
                 * experienced any oddities from this trade-off.
                 */
                return;
            }

            if (starImage.getUrl().endsWith(STAR_ACTIVE_URL)) {
                patternNameField.setText(DEFAULT_PATTERN_NAME);
                patternNameField.setVisible(true);
                patternNameField.selectAll();
                patternNameField.setFocus(true);
                patternNameLabel.setVisible(false);
            } else if (starImage.getUrl().endsWith(STAR_ON_URL)) {
                starImage.setUrl(STAR_ACTIVE_URL);
                patternNameField.setVisible(false);
                patternNameLabel.setVisible(false);
                savedSearchManager.removePatternByName(currentSearch);
            }
        }

        @Override
        public void onMouseOver(MouseOverEvent event) {
            if (starImage.getUrl().endsWith(STAR_OFF_URL)) {
                starImage.setUrl(STAR_ACTIVE_URL);

            }
        }

        @Override
        public void onMouseOut(MouseOutEvent event) {
            if (starImage.getUrl().endsWith(STAR_ACTIVE_URL) && !patternNameField.isVisible()) {
                starImage.setUrl(STAR_OFF_URL);
            }
        }
    }

    class ArrowImageEventHandler implements ClickHandler {
        @Override
        public void onClick(ClickEvent event) {
            savedSearches.updateModel();
            int left = autoCompletePatternField.getAbsoluteLeft();
            int top = autoCompletePatternField.getAbsoluteTop() + autoCompletePatternField.getOffsetHeight();
            savedSearchesPanel.setPopupPosition(left, top + 5);
            savedSearchesPanel.show();
            arrowImage.setUrl(ARROW_GRAY_URL);
        }
    }

    class SavedSearchesEventHandler implements CloseHandler<PopupPanel>, PatternSelectionHandler {
        @Override
        public void onClose(CloseEvent<PopupPanel> event) {
            arrowImage.setUrl(ARROW_WHITE_URL);
        }

        @Override
        public void handleSelection(int rowIndex, int columnIndex, String patternName) {
            if (columnIndex == 1) {
                savedSearchManager.removePatternByName(patternName);

                if (currentSearch.equals(patternName)) {
                    currentSearch = "";
                    patternNameField.setValue("", true);
                    patternNameField.setVisible(false);
                    patternNameLabel.setText("");
                    patternNameLabel.setVisible(false);
                    autoCompletePatternField.setFocus(true);
                    starImage.setUrl(STAR_OFF_URL);
                    savedSearchesPanel.hide();
                }

                if (savedSearchManager.getSavedSearchCount() == 0) {
                    savedSearchesPanel.hide();
                }

                savedSearches.removeRow(rowIndex);
            } else {
                currentSearch = "";
                String patternValue = savedSearchManager.getPatternByName(patternName);
                autoCompletePatternField.setValue(patternValue, true);
                patternNameField.setValue(patternName, true);
                SearchLogger.debug("search results change: [" + patternName + "," + patternValue + "]");
                turnNameFieldIntoLabel();
                savedSearchesPanel.hide();
                executeSearch();
            }
        }
    }

    class SearchButtonEventHandler implements ClickHandler {
        @Override
        public void onClick(ClickEvent event) {
            executeSearch();
        }
    }

}