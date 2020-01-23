/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package dev.swine.kubernetes.pagercontroller.model;

public interface HasStatus<S extends Status> {
    S getStatus();
}
