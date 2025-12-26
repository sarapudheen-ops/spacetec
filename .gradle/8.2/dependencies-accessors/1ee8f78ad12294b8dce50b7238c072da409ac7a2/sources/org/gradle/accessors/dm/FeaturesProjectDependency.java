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
public class FeaturesProjectDependency extends DelegatingProjectDependency {

    @Inject
    public FeaturesProjectDependency(TypeSafeProjectDependencyFactory factory, ProjectDependencyInternal delegate) {
        super(factory, delegate);
    }

    /**
     * Creates a project dependency on the project at path ":features:bidirectional"
     */
    public Features_BidirectionalProjectDependency getBidirectional() { return new Features_BidirectionalProjectDependency(getFactory(), create(":features:bidirectional")); }

    /**
     * Creates a project dependency on the project at path ":features:coding"
     */
    public Features_CodingProjectDependency getCoding() { return new Features_CodingProjectDependency(getFactory(), create(":features:coding")); }

    /**
     * Creates a project dependency on the project at path ":features:connection"
     */
    public Features_ConnectionProjectDependency getConnection() { return new Features_ConnectionProjectDependency(getFactory(), create(":features:connection")); }

    /**
     * Creates a project dependency on the project at path ":features:dashboard"
     */
    public Features_DashboardProjectDependency getDashboard() { return new Features_DashboardProjectDependency(getFactory(), create(":features:dashboard")); }

    /**
     * Creates a project dependency on the project at path ":features:dtc"
     */
    public Features_DtcProjectDependency getDtc() { return new Features_DtcProjectDependency(getFactory(), create(":features:dtc")); }

    /**
     * Creates a project dependency on the project at path ":features:ecu"
     */
    public Features_EcuProjectDependency getEcu() { return new Features_EcuProjectDependency(getFactory(), create(":features:ecu")); }

    /**
     * Creates a project dependency on the project at path ":features:freezeframe"
     */
    public Features_FreezeframeProjectDependency getFreezeframe() { return new Features_FreezeframeProjectDependency(getFactory(), create(":features:freezeframe")); }

    /**
     * Creates a project dependency on the project at path ":features:keyprogramming"
     */
    public Features_KeyprogrammingProjectDependency getKeyprogramming() { return new Features_KeyprogrammingProjectDependency(getFactory(), create(":features:keyprogramming")); }

    /**
     * Creates a project dependency on the project at path ":features:livedata"
     */
    public Features_LivedataProjectDependency getLivedata() { return new Features_LivedataProjectDependency(getFactory(), create(":features:livedata")); }

    /**
     * Creates a project dependency on the project at path ":features:maintenance"
     */
    public Features_MaintenanceProjectDependency getMaintenance() { return new Features_MaintenanceProjectDependency(getFactory(), create(":features:maintenance")); }

    /**
     * Creates a project dependency on the project at path ":features:reports"
     */
    public Features_ReportsProjectDependency getReports() { return new Features_ReportsProjectDependency(getFactory(), create(":features:reports")); }

    /**
     * Creates a project dependency on the project at path ":features:settings"
     */
    public Features_SettingsProjectDependency getSettings() { return new Features_SettingsProjectDependency(getFactory(), create(":features:settings")); }

    /**
     * Creates a project dependency on the project at path ":features:vehicleinfo"
     */
    public Features_VehicleinfoProjectDependency getVehicleinfo() { return new Features_VehicleinfoProjectDependency(getFactory(), create(":features:vehicleinfo")); }

}
