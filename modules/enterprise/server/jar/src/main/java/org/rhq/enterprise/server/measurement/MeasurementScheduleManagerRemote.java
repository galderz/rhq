/*
 * RHQ Management Platform
 * Copyright (C) 2005-2008 Red Hat, Inc.
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
package org.rhq.enterprise.server.measurement;

import javax.ejb.Remote;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.criteria.MeasurementScheduleCriteria;
import org.rhq.core.domain.measurement.MeasurementSchedule;
import org.rhq.core.domain.util.PageList;
import org.rhq.enterprise.server.system.ServerVersion;

@SOAPBinding(style = SOAPBinding.Style.DOCUMENT)
@WebService(targetNamespace = ServerVersion.namespace)
@Remote
public interface MeasurementScheduleManagerRemote {

    @WebMethod
    void disableSchedulesForResource(//
        @WebParam(name = "subject") Subject subject, //
        @WebParam(name = "resourceId") int resourceId, //
        @WebParam(name = "measurementDefinitionIds") int[] measurementDefinitionIds);

    @WebMethod
    void disableSchedulesForCompatibleGroup(//
        @WebParam(name = "subject") Subject subject, //
        @WebParam(name = "groupId") int groupId, //
        @WebParam(name = "measurementDefinitionIds") int[] measurementDefinitionIds);

    @WebMethod
    void disableMeasurementTemplates(//
        @WebParam(name = "subject") Subject subject, //
        @WebParam(name = "measurementDefinitionIds") int[] measurementDefinitionIds);

    @WebMethod
    void enableSchedulesForResource(//
        @WebParam(name = "subject") Subject subject, //
        @WebParam(name = "resourceId") int resourceId, //
        @WebParam(name = "measurementDefinitionIds") int[] measurementDefinitionIds);

    @WebMethod
    void enableSchedulesForCompatibleGroup(//
        @WebParam(name = "subject") Subject subject, //
        @WebParam(name = "groupId") int groupId, //
        @WebParam(name = "measurementDefinitionIds") int[] measurementDefinitionIds);

    @WebMethod
    void enableMeasurementTemplates(//
        @WebParam(name = "subject") Subject subject, //
        @WebParam(name = "measurementDefinitionIds") int[] measurementDefinitionIds);

    @WebMethod
    void updateSchedule( //
        @WebParam(name = "subject") Subject subject, //
        @WebParam(name = "measurementSchedule") MeasurementSchedule measurementSchedule);

    @WebMethod
    void updateSchedulesForResource(//
        @WebParam(name = "subject") Subject subject, //
        @WebParam(name = "resourceId") int resourceId, //
        @WebParam(name = "measurementDefinitionIds") int[] measurementDefinitionIds, //
        @WebParam(name = "collectionInterval") long collectionInterval);

    @WebMethod
    void updateSchedulesForCompatibleGroup(//
        @WebParam(name = "subject") Subject subject, //
        @WebParam(name = "resourceId") int groupId, //
        @WebParam(name = "measurementDefinitionIds") int[] measurementDefinitionIds, //
        @WebParam(name = "collectionInterval") long collectionInterval);

    @WebMethod
    void updateMeasurementTemplates(//
        @WebParam(name = "subject") Subject subject, //
        @WebParam(name = "measurementDefinitionIds") int[] measurementDefinitionIds, //
        @WebParam(name = "collectionInterval") long collectionInterval);

    @WebMethod
    PageList<MeasurementSchedule> findSchedulesByCriteria(//
        @WebParam(name = "subject") Subject subject, //
        @WebParam(name = "criteria") MeasurementScheduleCriteria criteria);

}
