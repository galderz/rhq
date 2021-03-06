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

package org.rhq.enterprise.server.alert.test;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.transaction.TransactionManager;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.rhq.core.domain.alert.Alert;
import org.rhq.core.domain.alert.AlertCondition;
import org.rhq.core.domain.alert.AlertConditionCategory;
import org.rhq.core.domain.alert.AlertConditionLog;
import org.rhq.core.domain.alert.AlertDampening;
import org.rhq.core.domain.alert.AlertDefinition;
import org.rhq.core.domain.alert.AlertPriority;
import org.rhq.core.domain.alert.BooleanExpression;
import org.rhq.core.domain.alert.AlertDampening.Category;
import org.rhq.core.domain.cloud.Server;
import org.rhq.core.domain.criteria.AlertCriteria;
import org.rhq.core.domain.criteria.ResourceCriteria;
import org.rhq.core.domain.measurement.MeasurementDataNumeric;
import org.rhq.core.domain.measurement.MeasurementDefinition;
import org.rhq.core.domain.measurement.MeasurementReport;
import org.rhq.core.domain.measurement.MeasurementSchedule;
import org.rhq.core.domain.measurement.MeasurementScheduleRequest;
import org.rhq.core.domain.resource.Agent;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.core.domain.util.PageControl;
import org.rhq.core.domain.util.PageList;
import org.rhq.enterprise.server.alert.AlertDefinitionManagerLocal;
import org.rhq.enterprise.server.alert.AlertManagerLocal;
import org.rhq.enterprise.server.measurement.MeasurementDataManagerLocal;
import org.rhq.enterprise.server.resource.metadata.test.UpdatePluginMetadataTestBase;
import org.rhq.enterprise.server.util.LookupUtil;
import org.rhq.test.JPAUtils;
import org.rhq.test.TransactionCallbackWithContext;

@Test
public class AlertConditionTest extends UpdatePluginMetadataTestBase {
    // this must match the constant found in ServerManagerBean
    private static final String RHQ_SERVER_NAME_PROPERTY = "rhq.server.high-availability.name";
    private static final String RHQ_SERVER_NAME_PROPERTY_VALUE = "AlertConditionTestServer";

    private Resource resource;
    private Server server;

    @Override
    protected String getSubsystemDirectory() {
        return "alerts";
    }

    @AfterMethod(alwaysRun = true)
    public void removePersistedResource() throws Exception {
        if (resource != null) {
            deleteNewResource(resource);
            resource = null;
        }

        deleteServerIdentity();

        return;
    }

    @BeforeClass(alwaysRun = true)
    public void setSysProp() {
        System.setProperty(RHQ_SERVER_NAME_PROPERTY, RHQ_SERVER_NAME_PROPERTY_VALUE);
    }

    @AfterClass(alwaysRun = true)
    public void unsetSysProp() {
        System.setProperty(RHQ_SERVER_NAME_PROPERTY, "");
    }

    public void testBZ735262_InsideRangeCondition() throws Exception {
        // create our resource with alert definition
        MeasurementDefinition metricDef = createResourceWithMetricSchedule();
        createAlertDefinitionWithOneInsideRangeCondition(metricDef, resource.getId()); // this test would fail if we used createAlertDefinitionWithTwoConditionsALL

        // re-load the resource so we get the measurement schedule
        Resource resourceWithSchedules = loadResourceWithSchedules(resource.getId());
        MeasurementSchedule schedule = resourceWithSchedules.getSchedules().iterator().next();

        // simulate a measurement report coming from the agent - two values, but neither fit in our range, so no alerts are fired
        MeasurementScheduleRequest request = new MeasurementScheduleRequest(schedule);
        MeasurementReport report = new MeasurementReport();
        report.addData(new MeasurementDataNumeric(getTimestamp(60), request, Double.valueOf(20.0))); // 20 < 60 but !(20 > 40)
        report.addData(new MeasurementDataNumeric(getTimestamp(30), request, Double.valueOf(70.0))); // !(70 < 60) but 70 > 40
        MeasurementDataManagerLocal dataManager = LookupUtil.getMeasurementDataManager();
        dataManager.mergeMeasurementReport(report);

        // wait for our JMS messages to process and see if we get any alerts
        Thread.sleep(5000);

        // make sure no alert was triggered
        PageList<Alert> alerts = getAlerts(resourceWithSchedules.getId());
        assert alerts.size() == 0 : "no alerts should have fired: " + alerts;

        // simulate another measurement report coming from the agent - one values that fits in our range, so 1 alert is fired
        request = new MeasurementScheduleRequest(schedule);
        report = new MeasurementReport();
        report.addData(new MeasurementDataNumeric(getTimestamp(15), request, Double.valueOf(50.0))); // 50 < 60 AND 50 > 40
        dataManager.mergeMeasurementReport(report);

        // wait for our JMS messages to process and see if we get any alerts
        Thread.sleep(5000);

        // make sure one alert was triggered
        alerts = getAlerts(resourceWithSchedules.getId());
        assert alerts.size() == 1 : "1 alert should have fired: " + alerts;
        return;
    }

    public void testBZ735262_OutsideRangeCondition() throws Exception {
        // create our resource with alert definition
        MeasurementDefinition metricDef = createResourceWithMetricSchedule();
        createAlertDefinitionWithOneOutsideRangeCondition(metricDef, resource.getId()); // this test would fail if we used createAlertDefinitionWithTwoConditionsALL

        // re-load the resource so we get the measurement schedule
        Resource resourceWithSchedules = loadResourceWithSchedules(resource.getId());
        MeasurementSchedule schedule = resourceWithSchedules.getSchedules().iterator().next();

        // simulate a measurement report coming from the agent - one value that is inside our range, so no alerts are fired
        MeasurementScheduleRequest request = new MeasurementScheduleRequest(schedule);
        MeasurementReport report = new MeasurementReport();
        report.addData(new MeasurementDataNumeric(getTimestamp(60), request, Double.valueOf(50.0))); // 50 is inside the range 40...60
        MeasurementDataManagerLocal dataManager = LookupUtil.getMeasurementDataManager();
        dataManager.mergeMeasurementReport(report);

        // wait for our JMS messages to process and see if we get any alerts
        Thread.sleep(5000);

        // make sure no alert was triggered
        PageList<Alert> alerts = getAlerts(resourceWithSchedules.getId());
        assert alerts.size() == 0 : "no alerts should have fired: " + alerts;

        // simulate another measurement report coming from the agent - one value that is outside the range, so 1 alert is fired
        request = new MeasurementScheduleRequest(schedule);
        report = new MeasurementReport();
        report.addData(new MeasurementDataNumeric(getTimestamp(15), request, Double.valueOf(20.0))); // 20 is outside 40...60
        dataManager.mergeMeasurementReport(report);

        // wait for our JMS messages to process and see if we get any alerts
        Thread.sleep(5000);

        // make sure one alert was triggered
        alerts = getAlerts(resourceWithSchedules.getId());
        assert alerts.size() == 1 : "1 alert should have fired: " + alerts;
        return;
    }

    public void testBZ736685_DeleteConditionLogButNoAlert() throws Exception {
        // create our resource with alert definition
        MeasurementDefinition metricDef = createResourceWithMetricSchedule();
        AlertDefinition alertDef = createAlertDefinitionWithTwoConditionsALL(metricDef, resource.getId());

        // re-load the resource so we get the measurement schedule
        Resource resourceWithSchedules = loadResourceWithSchedules(resource.getId());
        MeasurementSchedule schedule = resourceWithSchedules.getSchedules().iterator().next();

        // simulate a measurement report coming from the agent
        // with a single metric that makes a condition trigger but does not fire an alert
        MeasurementScheduleRequest request = new MeasurementScheduleRequest(schedule);
        MeasurementReport report = new MeasurementReport();
        report.addData(new MeasurementDataNumeric(getTimestamp(60), request, Double.valueOf(20.0))); // 20 < 60 but !(20 > 40)
        MeasurementDataManagerLocal dataManager = LookupUtil.getMeasurementDataManager();
        dataManager.mergeMeasurementReport(report);

        // wait for our JMS messages to process and see if we get any alerts
        Thread.sleep(5000);

        PageList<Alert> alerts = getAlerts(resourceWithSchedules.getId());
        assert alerts.size() == 0 : "no alerts should have fired: " + alerts;

        // ...but make sure a condition was true (the condition we know is true is "< 60")
        int condId = 0;
        for (AlertCondition cond : alertDef.getConditions()) {
            if (cond.getComparator().equals("<")) {
                condId = cond.getId();
                break;
            }
        }
        assert condId > 0 : "failed to get the condition ID - something is wrong with test setup";
        AlertCondition conditionWithLogs = getAlertConditionWithLogs(condId); // should eagerly load logs
        Set<AlertConditionLog> logs = conditionWithLogs.getConditionLogs();
        assert logs != null && logs.size() > 0 : "missing condition log - we should have one that was generated";
        AlertConditionLog log = logs.iterator().next();
        assert log.getAlert() == null : "condition should not have fired an alert: " + logs;

        // purge the resource fully, which should remove all alert defs and alert conditions and condition logs
        int resourceId = resource.getId();
        deleteNewResource(resource);
        resource = null;

        AlertDefinitionManagerLocal alertDefManager = LookupUtil.getAlertDefinitionManager();
        PageList<AlertDefinition> defs = alertDefManager.findAlertDefinitions(getOverlord(), resourceId, PageControl
            .getUnlimitedInstance());
        assert defs.isEmpty() : "failed to delete the alert definition - are condition logs still around?";

        return;
    }

    private PageList<Alert> getAlerts(int resourceId) {
        AlertManagerLocal alertManager = LookupUtil.getAlertManager();
        AlertCriteria alertCriteria = new AlertCriteria();
        alertCriteria.addFilterResourceIds(resourceId);
        PageList<Alert> alerts = alertManager.findAlertsByCriteria(getOverlord(), alertCriteria);
        return alerts;
    }

    private AlertCondition getAlertConditionWithLogs(final int conditionId) {
        return JPAUtils.executeInTransaction(new TransactionCallbackWithContext<AlertCondition>() {
            @Override
            public AlertCondition execute(TransactionManager tm, EntityManager em) throws Exception {
                AlertCondition cond = em.find(AlertCondition.class, conditionId);
                cond.getConditionLogs().size(); // force the load
                return cond;
            }
        });
    }

    private Resource loadResourceWithSchedules(int resourceId) {
        ResourceCriteria resourceCriteria = new ResourceCriteria();
        resourceCriteria.addFilterId(resourceId);
        resourceCriteria.fetchSchedules(true);
        Resource resourceWithSchedules = getResource(resourceCriteria);
        assert resourceWithSchedules != null : "could not obtain resource from DB";
        assert resourceWithSchedules.getSchedules() != null && resourceWithSchedules.getSchedules().size() == 1 : "missing schedule";
        return resourceWithSchedules;
    }

    private AlertDefinition createAlertDefinitionWithTwoConditionsALL(MeasurementDefinition metricDef, int resourceId) {
        // create alert definition with the conditions "metric value > 40 AND metric value < 60"
        HashSet<AlertCondition> conditions = new HashSet<AlertCondition>(2);
        AlertCondition cond1 = new AlertCondition();
        cond1.setCategory(AlertConditionCategory.THRESHOLD);
        cond1.setName(metricDef.getDisplayName());
        cond1.setComparator(">");
        cond1.setThreshold(Double.valueOf(40.0)); // value > 40 threshold
        cond1.setOption(null);
        cond1.setMeasurementDefinition(metricDef);
        conditions.add(cond1);

        AlertCondition cond2 = new AlertCondition();
        cond2.setCategory(AlertConditionCategory.THRESHOLD);
        cond2.setName(metricDef.getDisplayName());
        cond2.setComparator("<");
        cond2.setThreshold(Double.valueOf(60.0)); // value < 60 threshold
        cond2.setOption(null);
        cond2.setMeasurementDefinition(metricDef);
        conditions.add(cond2);

        AlertDefinition alertDefinition = new AlertDefinition();
        alertDefinition.setName("two condition ALL alert");
        alertDefinition.setEnabled(true);
        alertDefinition.setPriority(AlertPriority.HIGH);
        alertDefinition.setAlertDampening(new AlertDampening(Category.NONE));
        alertDefinition.setRecoveryId(Integer.valueOf(0));
        alertDefinition.setConditionExpression(BooleanExpression.ALL);
        alertDefinition.setConditions(conditions);

        AlertDefinitionManagerLocal alertDefManager = LookupUtil.getAlertDefinitionManager();
        int defId = alertDefManager.createAlertDefinition(getOverlord(), alertDefinition, resourceId);
        alertDefinition = alertDefManager.getAlertDefinition(getOverlord(), defId); // load it back so we get its ID and all condition IDs
        assert alertDefinition != null && alertDefinition.getId() > 0 : "did not persist alert def properly: "
            + alertDefinition;

        // now that we created an alert def, we have to reload the alert condition cache
        reloadAllAlertConditionCaches();

        return alertDefinition;
    }

    private AlertDefinition createAlertDefinitionWithOneInsideRangeCondition(MeasurementDefinition metricDef,
        int resourceId) {
        // create alert definition with the range condition "metric value between 40...60"
        HashSet<AlertCondition> conditions = new HashSet<AlertCondition>(1);
        AlertCondition cond1 = new AlertCondition();
        cond1.setCategory(AlertConditionCategory.RANGE);
        cond1.setName(metricDef.getDisplayName());
        cond1.setThreshold(Double.valueOf(40.0)); // threshold is always the low value of the range
        cond1.setOption(Double.valueOf(60.0).toString()); // option is a stringified double that is always the high value of the range
        cond1.setComparator("<"); // the value must be inside the range
        cond1.setMeasurementDefinition(metricDef);
        conditions.add(cond1);

        AlertDefinition alertDefinition = new AlertDefinition();
        alertDefinition.setName("one inside-range condition alert");
        alertDefinition.setEnabled(true);
        alertDefinition.setPriority(AlertPriority.HIGH);
        alertDefinition.setAlertDampening(new AlertDampening(Category.NONE));
        alertDefinition.setRecoveryId(Integer.valueOf(0));
        alertDefinition.setConditionExpression(BooleanExpression.ALL);
        alertDefinition.setConditions(conditions);

        AlertDefinitionManagerLocal alertDefManager = LookupUtil.getAlertDefinitionManager();
        int defId = alertDefManager.createAlertDefinition(getOverlord(), alertDefinition, resourceId);
        alertDefinition = alertDefManager.getAlertDefinition(getOverlord(), defId); // load it back so we get its ID and all condition IDs
        assert alertDefinition != null && alertDefinition.getId() > 0 : "did not persist alert def properly: "
            + alertDefinition;

        // now that we created an alert def, we have to reload the alert condition cache
        reloadAllAlertConditionCaches();

        return alertDefinition;
    }

    private AlertDefinition createAlertDefinitionWithOneOutsideRangeCondition(MeasurementDefinition metricDef,
        int resourceId) {
        // create alert definition with the range condition "metric value outside 40...60"
        HashSet<AlertCondition> conditions = new HashSet<AlertCondition>(1);
        AlertCondition cond1 = new AlertCondition();
        cond1.setCategory(AlertConditionCategory.RANGE);
        cond1.setName(metricDef.getDisplayName());
        cond1.setThreshold(Double.valueOf(40.0)); // threshold is always the low value of the range
        cond1.setOption(Double.valueOf(60.0).toString()); // option is a stringified double that is always the high value of the range
        cond1.setComparator(">"); // the value must be outside the range
        cond1.setMeasurementDefinition(metricDef);
        conditions.add(cond1);

        AlertDefinition alertDefinition = new AlertDefinition();
        alertDefinition.setName("one outside-range condition alert");
        alertDefinition.setEnabled(true);
        alertDefinition.setPriority(AlertPriority.HIGH);
        alertDefinition.setAlertDampening(new AlertDampening(Category.NONE));
        alertDefinition.setRecoveryId(Integer.valueOf(0));
        alertDefinition.setConditionExpression(BooleanExpression.ALL);
        alertDefinition.setConditions(conditions);

        AlertDefinitionManagerLocal alertDefManager = LookupUtil.getAlertDefinitionManager();
        int defId = alertDefManager.createAlertDefinition(getOverlord(), alertDefinition, resourceId);
        alertDefinition = alertDefManager.getAlertDefinition(getOverlord(), defId); // load it back so we get its ID and all condition IDs
        assert alertDefinition != null && alertDefinition.getId() > 0 : "did not persist alert def properly: "
            + alertDefinition;

        // now that we created an alert def, we have to reload the alert condition cache
        reloadAllAlertConditionCaches();

        return alertDefinition;
    }

    /**
     * Creates a resource, stores it in the "resource" data field and returns the measurement definition
     * that the schedule is for.
     * 
     * @return measurement definition that was used to create the schedule for the new resource
     * @throws Exception
     */
    private MeasurementDefinition createResourceWithMetricSchedule() throws Exception {
        registerPlugin("type-with-metric.xml");
        ResourceType resourceType = getResourceType("TypeWithMetrics");
        assert resourceType != null : "failed to deploy resource type";
        assert resourceType.getMetricDefinitions() != null : "failed to create metric defs";
        assert resourceType.getMetricDefinitions().size() == 1 : "do not have the expected number of metric defs";

        final MeasurementDefinition metricDef = resourceType.getMetricDefinitions().iterator().next();

        resource = persistNewResource(resourceType.getName());
        assert resource != null && resource.getId() > 0 : "failed to create test resource";

        JPAUtils.executeInTransaction(new TransactionCallbackWithContext<Object>() {
            @Override
            public Object execute(TransactionManager tm, EntityManager em) throws Exception {
                MeasurementSchedule schedule = new MeasurementSchedule(metricDef, resource);
                em.persist(schedule);
                return null;
            }
        });

        // create a server which attaches our agent to it - we need this for the alert subsystem to do its thing
        createServerIdentity();
        return metricDef;
    }

    /**
     * Returns a epoch millis timestamp that is the current time minus the given number of seconds.
     * In other words, this returns a time in the past - how far in the past is determined by the
     * number of seconds parameter.
     * @param secondsAgo
     */
    private long getTimestamp(long secondsAgo) {
        return System.currentTimeMillis() - (secondsAgo * 1000);
    }

    private void reloadAllAlertConditionCaches() {
        LookupUtil.getAlertConditionCacheManager().reloadAllCaches();
    }

    private void createServerIdentity() {
        server = new Server();
        server.setName(RHQ_SERVER_NAME_PROPERTY_VALUE);
        server.setAddress("localhost");
        server.setPort(7080);
        server.setSecurePort(7443);
        server.setComputePower(1);
        server.setOperationMode(Server.OperationMode.MAINTENANCE);
        int serverId = LookupUtil.getServerManager().create(server);
        assert serverId > 0 : "could not create our server identity in the DB";

        // simulate the agent being "connected" to the server
        try {
            Agent agent = getAgent(getEntityManager());
            agent.setServer(server);
            LookupUtil.getAgentManager().updateAgent(agent);
        } catch (NoResultException nre) {
            // no agent to attach
        }
    }

    private void deleteServerIdentity() throws Exception {
        if (server != null) {
            cleanupAgent(); // can't remove the server before we purge the agent
            LookupUtil.getCloudManager().deleteServer(server.getId());
            server = null;
        }
    }
}
