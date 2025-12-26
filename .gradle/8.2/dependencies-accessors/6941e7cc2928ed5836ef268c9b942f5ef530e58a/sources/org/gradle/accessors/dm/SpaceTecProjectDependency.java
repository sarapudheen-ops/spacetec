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
public class SpaceTecProjectDependency extends DelegatingProjectDependency {

    @Inject
    public SpaceTecProjectDependency(TypeSafeProjectDependencyFactory factory, ProjectDependencyInternal delegate) {
        super(factory, delegate);
    }

    /**
     * Creates a project dependency on the project at path ":analysis"
     */
    public AnalysisProjectDependency getAnalysis() { return new AnalysisProjectDependency(getFactory(), create(":analysis")); }

    /**
     * Creates a project dependency on the project at path ":app"
     */
    public AppProjectDependency getApp() { return new AppProjectDependency(getFactory(), create(":app")); }

    /**
     * Creates a project dependency on the project at path ":compliance"
     */
    public ComplianceProjectDependency getCompliance() { return new ComplianceProjectDependency(getFactory(), create(":compliance")); }

    /**
     * Creates a project dependency on the project at path ":core"
     */
    public CoreProjectDependency getCore() { return new CoreProjectDependency(getFactory(), create(":core")); }

    /**
     * Creates a project dependency on the project at path ":features"
     */
    public FeaturesProjectDependency getFeatures() { return new FeaturesProjectDependency(getFactory(), create(":features")); }

    /**
     * Creates a project dependency on the project at path ":protocol"
     */
    public ProtocolProjectDependency getProtocol() { return new ProtocolProjectDependency(getFactory(), create(":protocol")); }

    /**
     * Creates a project dependency on the project at path ":scanner"
     */
    public ScannerProjectDependency getScanner() { return new ScannerProjectDependency(getFactory(), create(":scanner")); }

    /**
     * Creates a project dependency on the project at path ":vehicle"
     */
    public VehicleProjectDependency getVehicle() { return new VehicleProjectDependency(getFactory(), create(":vehicle")); }

}
