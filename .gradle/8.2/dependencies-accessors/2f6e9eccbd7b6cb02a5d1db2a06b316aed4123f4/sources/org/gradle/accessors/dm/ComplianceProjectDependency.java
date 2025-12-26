package org.gradle.accessors.dm;

import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.catalog.DelegatingProjectDependency;
import org.gradle.api.internal.catalog.TypeSafeProjectDependencyFactory;
import javax.inject.Inject;

@NonNullApi
public class ComplianceProjectDependency extends DelegatingProjectDependency {

    @Inject
    public ComplianceProjectDependency(TypeSafeProjectDependencyFactory factory, ProjectDependencyInternal delegate) {
        super(factory, delegate);
    }

    /**
     * Creates a project dependency on the project at path ":compliance:carb"
     */
    public Compliance_CarbProjectDependency getCarb() { return new Compliance_CarbProjectDependency(getFactory(), create(":compliance:carb")); }

    /**
     * Creates a project dependency on the project at path ":compliance:china6"
     */
    public Compliance_China6ProjectDependency getChina6() { return new Compliance_China6ProjectDependency(getFactory(), create(":compliance:china6")); }

    /**
     * Creates a project dependency on the project at path ":compliance:core"
     */
    public Compliance_CoreProjectDependency getCore() { return new Compliance_CoreProjectDependency(getFactory(), create(":compliance:core")); }

    /**
     * Creates a project dependency on the project at path ":compliance:emissions"
     */
    public Compliance_EmissionsProjectDependency getEmissions() { return new Compliance_EmissionsProjectDependency(getFactory(), create(":compliance:emissions")); }

    /**
     * Creates a project dependency on the project at path ":compliance:euro6"
     */
    public Compliance_Euro6ProjectDependency getEuro6() { return new Compliance_Euro6ProjectDependency(getFactory(), create(":compliance:euro6")); }

    /**
     * Creates a project dependency on the project at path ":compliance:india-bs6"
     */
    public Compliance_IndiaBs6ProjectDependency getIndiaBs6() { return new Compliance_IndiaBs6ProjectDependency(getFactory(), create(":compliance:india-bs6")); }

}
