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
public class ScannerProjectDependency extends DelegatingProjectDependency {

    @Inject
    public ScannerProjectDependency(TypeSafeProjectDependencyFactory factory, ProjectDependencyInternal delegate) {
        super(factory, delegate);
    }

    /**
     * Creates a project dependency on the project at path ":scanner:bluetooth"
     */
    public Scanner_BluetoothProjectDependency getBluetooth() { return new Scanner_BluetoothProjectDependency(getFactory(), create(":scanner:bluetooth")); }

    /**
     * Creates a project dependency on the project at path ":scanner:core"
     */
    public Scanner_CoreProjectDependency getCore() { return new Scanner_CoreProjectDependency(getFactory(), create(":scanner:core")); }

    /**
     * Creates a project dependency on the project at path ":scanner:elm327"
     */
    public Scanner_Elm327ProjectDependency getElm327() { return new Scanner_Elm327ProjectDependency(getFactory(), create(":scanner:elm327")); }

    /**
     * Creates a project dependency on the project at path ":scanner:j2534"
     */
    public Scanner_J2534ProjectDependency getJ2534() { return new Scanner_J2534ProjectDependency(getFactory(), create(":scanner:j2534")); }

    /**
     * Creates a project dependency on the project at path ":scanner:usb"
     */
    public Scanner_UsbProjectDependency getUsb() { return new Scanner_UsbProjectDependency(getFactory(), create(":scanner:usb")); }

    /**
     * Creates a project dependency on the project at path ":scanner:wifi"
     */
    public Scanner_WifiProjectDependency getWifi() { return new Scanner_WifiProjectDependency(getFactory(), create(":scanner:wifi")); }

}
