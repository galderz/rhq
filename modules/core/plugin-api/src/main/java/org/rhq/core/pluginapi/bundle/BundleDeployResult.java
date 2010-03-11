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
package org.rhq.core.pluginapi.bundle;

import java.io.Serializable;

import org.rhq.core.util.exception.ThrowableUtil;

/**
 * The results after an attempt to deploy a bundle has been completed.
 * @author John Mazzitelli
 */
public class BundleDeployResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private String errorMessage;

    public boolean isSuccess() {
        return this.errorMessage == null;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setErrorMessage(Throwable t) {
        this.errorMessage = ThrowableUtil.getAllMessages(t);
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder(this.getClass().getSimpleName());
        if (getErrorMessage() != null) {
            str.append(" error=[").append(getErrorMessage()).append("];");
        }
        str.append(" success=[").append(isSuccess()).append("]");
        return str.toString();
    }

}