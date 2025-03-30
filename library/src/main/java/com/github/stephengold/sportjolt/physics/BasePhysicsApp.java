/*
 Copyright (c) 2022-2025 Stephen Gold and Yanis Boudiaf

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:

 1. Redistributions of source code must retain the above copyright notice, this
    list of conditions and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

 3. Neither the name of the copyright holder nor the names of its
    contributors may be used to endorse or promote products derived from
    this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.github.stephengold.sportjolt.physics;

import com.github.stephengold.joltjni.CharacterVirtual;
import com.github.stephengold.joltjni.JobSystem;
import com.github.stephengold.joltjni.JobSystemThreadPool;
import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.JoltPhysicsObject;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.TempAllocator;
import com.github.stephengold.joltjni.TempAllocatorImpl;
import com.github.stephengold.joltjni.VehicleConstraint;
import com.github.stephengold.joltjni.enumerate.EShapeSubType;
import com.github.stephengold.joltjni.readonly.ConstBody;
import com.github.stephengold.joltjni.readonly.ConstCharacter;
import com.github.stephengold.joltjni.readonly.ConstCharacterVirtual;
import com.github.stephengold.joltjni.readonly.ConstJoltPhysicsObject;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.github.stephengold.sportjolt.BaseApplication;
import com.github.stephengold.sportjolt.Constants;
import com.github.stephengold.sportjolt.Filter;
import com.github.stephengold.sportjolt.FlipAxes;
import com.github.stephengold.sportjolt.Geometry;
import com.github.stephengold.sportjolt.Mesh;
import com.github.stephengold.sportjolt.NormalsOption;
import com.github.stephengold.sportjolt.TextureKey;
import com.github.stephengold.sportjolt.UvsOption;
import com.github.stephengold.sportjolt.Validate;
import com.github.stephengold.sportjolt.WrapFunction;
import electrostatic4j.snaploader.LibraryInfo;
import electrostatic4j.snaploader.LoadingCriterion;
import electrostatic4j.snaploader.NativeBinaryLoader;
import electrostatic4j.snaploader.filesystem.DirectoryPath;
import electrostatic4j.snaploader.platform.NativeDynamicLibrary;
import electrostatic4j.snaploader.platform.util.PlatformPredicate;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;
import org.joml.Vector4f;

/**
 * An application to visualize 3-D physics.
 */
public abstract class BasePhysicsApp extends BaseApplication {
    // *************************************************************************
    // constants

    /**
     * customary number of object layers
     */
    final public static int numObjLayers = 2;
    /**
     * number of worker threads to use
     */
    final private static int numWorkerThreads
            = Runtime.getRuntime().availableProcessors();
    /**
     * customary object layer for moving objects
     */
    final public static int objLayerMoving = 0;
    /**
     * customary object layer for non-moving objects
     */
    final public static int objLayerNonMoving = 1;
    /**
     * expected version string of the Jolt-JNI native library
     */
    final private static String expectedVersion = "0.9.8";
    // *************************************************************************
    // fields

    /**
     * time step (in seconds, &gt;0)
     */
    protected float timePerStep = 1f / 60f;
    /**
     * simulation lag (for {@code maxSubSteps>0} in seconds, &ge;0)
     */
    private float physicsLag;
    /**
     * how many times render() has been invoked
     */
    private int renderCount;
    /**
     * schedule simulation jobs
     */
    private JobSystem jobSystem;
    /**
     * timestamp of the previous render() if renderCount > 0
     */
    private long lastPhysicsUpdate;
    /**
     * callbacks invoked before each simulation step
     */
    final private static Collection<PhysicsTickListener> tickListeners
            = new ArrayList<>(5);
    /**
     * map shape summaries to auto-generated meshes, for reuse
     */
    final private static Map<ShapeSummary, Mesh> meshCache
            = new WeakHashMap<>(200);
    /**
     * system for physics simulation
     */
    protected PhysicsSystem physicsSystem;
    /**
     * allocate temporary memory for physics simulation
     */
    private TempAllocator tempAllocator;
    // *************************************************************************
    // constructors

    /**
     * Explicit no-arg constructor to avoid javadoc warnings from JDK 18+.
     */
    protected BasePhysicsApp() {
        // do nothing
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Add the specified physics-tick listener.
     *
     * @param listener the listener to add (not null, alias created)
     */
    public void addTickListener(PhysicsTickListener listener) {
        tickListeners.add(listener);
    }

    /**
     * Create the PhysicsSystem during initialization.
     *
     * @return a new object
     */
    protected abstract PhysicsSystem createSystem();

    /**
     * Load and initialize the Jolt-JNI native library.
     */
    public static void initializeJoltJni() {
        PlatformPredicate linuxWithFma = new PlatformPredicate(
                PlatformPredicate.LINUX_X86_64,
                "avx", "avx2", "bmi1", "f16c", "fma", "sse4_1", "sse4_2");
        PlatformPredicate windowsWithAvx2 = new PlatformPredicate(
                PlatformPredicate.WIN_X86_64,
                "avx", "avx2", "sse4_1", "sse4_2");

        LibraryInfo info
                = new LibraryInfo(null, "joltjni", DirectoryPath.USER_DIR);
        NativeBinaryLoader loader = new NativeBinaryLoader(info);

        NativeDynamicLibrary[] libraries = {
            new NativeDynamicLibrary("linux/aarch64/com/github/stephengold",
            PlatformPredicate.LINUX_ARM_64),
            new NativeDynamicLibrary("linux/armhf/com/github/stephengold",
            PlatformPredicate.LINUX_ARM_32),
            new NativeDynamicLibrary(
            "linux/x86-64-fma/com/github/stephengold",
            linuxWithFma), // This should precede vanilla LINUX_X86_64.

            new NativeDynamicLibrary("linux/x86-64/com/github/stephengold",
            PlatformPredicate.LINUX_X86_64),
            new NativeDynamicLibrary("osx/aarch64/com/github/stephengold",
            PlatformPredicate.MACOS_ARM_64),
            new NativeDynamicLibrary("osx/x86-64/com/github/stephengold",
            PlatformPredicate.MACOS_X86_64),
            new NativeDynamicLibrary(
            "windows/x86-64-avx2/com/github/stephengold",
            windowsWithAvx2), // This should precede vanilla WIN_X86_64.

            new NativeDynamicLibrary("windows/x86-64/com/github/stephengold",
            PlatformPredicate.WIN_X86_64)
        };
        loader.registerNativeLibraries(libraries)
                .initPlatformLibrary()
                .setRetryWithCleanExtraction(true);

        // Load a Jolt-JNI native library for this platform.
        try {
            loader.loadLibrary(LoadingCriterion.INCREMENTAL_LOADING);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to load a Jolt-JNI native library!");
        }

        printLibraryInfo(System.out);

        String jjVersion = Jolt.versionString();
        if (!jjVersion.equals(expectedVersion)) {
            System.err.println("Expected a v" + expectedVersion
                    + " native library but loaded v" + jjVersion + "!");
            System.err.flush();
        }

        //Jolt.setTraceAllocations(true); // to log Jolt-JNI heap allocations
        JoltPhysicsObject.startCleaner(); // to free Jolt objects automatically

        Jolt.registerDefaultAllocator();
        Jolt.installDefaultAssertCallback();
        Jolt.installDefaultTraceCallback();

        boolean success = Jolt.newFactory();
        assert success;
        Jolt.registerTypes();
    }

    /**
     * Return a Mesh to visualize the summarized shape.
     *
     * @param shape the shape to visualize (not null, unaffected)
     * @param summary a summary of the shape (not null)
     * @return an immutable Mesh (not null)
     */
    static Mesh meshForShape(ConstShape shape, ShapeSummary summary) {
        Mesh result;

        if (meshCache.containsKey(summary)) {
            result = meshCache.get(summary);

        } else {
            MeshingStrategy strategy = summary.meshingStrategy();
            result = strategy.applyTo(shape);
            result.makeImmutable();
            meshCache.put(summary, result);
        }

        return result;
    }

    /**
     * Add physics objects to the PhysicsSystem during initialization.
     */
    abstract protected void populateSystem();

    /**
     * Advance the physics simulation by the specified interval. Invoked during
     * each update.
     *
     * @param intervalSeconds the elapsed (real) time since the previous
     * invocation of {@code updatePhysics} (in seconds, &ge;0)
     */
    public void updatePhysics(float intervalSeconds) {
        assert physicsLag >= 0f : physicsLag;
        float timeSinceStep = physicsLag + intervalSeconds;
        int numSubSteps = (int) Math.floor(timeSinceStep / timePerStep);
        assert numSubSteps >= 0 : numSubSteps;
        this.physicsLag = timeSinceStep - numSubSteps * timePerStep;
        assert physicsLag >= 0f : physicsLag;

        if (numSubSteps > 4) {
            numSubSteps = 4;
        }

        for (int i = 0; i < numSubSteps; ++i) {
            // Notify any step listeners:
            for (PhysicsTickListener listeners : tickListeners) {
                listeners.prePhysicsTick(physicsSystem, timePerStep);
            }

            // Single-step the physics system:
            int collisionSteps = 1;
            physicsSystem.update(
                    timePerStep, collisionSteps, tempAllocator, jobSystem);

            // Notify any step listeners:
            for (PhysicsTickListener listeners : tickListeners) {
                listeners.physicsTick(physicsSystem, timePerStep);
            }
        }
    }

    /**
     * Visualize the local axes of the specified physics object.
     *
     * @param jpo the object to visualize (or null for world axes)
     * @param axisLength how much of each axis to visualize (in world units,
     * &ge;0)
     * @return an array of new, visible geometries
     */
    public static Geometry[] visualizeAxes(
            ConstJoltPhysicsObject jpo, float axisLength) {
        Validate.nonNegative(axisLength, "axis length");

        int numAxes = 3;
        Geometry[] result = new Geometry[numAxes];

        for (int ai = 0; ai < numAxes; ++ai) {
            result[ai] = new LocalAxisGeometry(jpo, ai, axisLength)
                    .setDepthTest(false);
        }

        return result;
    }

    /**
     * Visualize the collision shape of the specified physics object.
     *
     * @param jpo the physics object to visualize (not null)
     * @return a new, visible Geometry
     */
    public static Geometry visualizeShape(ConstJoltPhysicsObject jpo) {
        float uvScale = 1f;
        Geometry result = visualizeShape(jpo, uvScale);

        return result;
    }

    /**
     * Visualize the shape of the specified physics object.
     *
     * @param jpo the physics object to visualize (not null)
     * @param uvScale the UV scale factor to use (default=1)
     * @return a new, visible Geometry
     */
    public static Geometry visualizeShape(
            ConstJoltPhysicsObject jpo, float uvScale) {
        ConstShape shape;
        if (jpo instanceof ConstBody) {
            shape = ((ConstBody) jpo).getShape();
        } else if (jpo instanceof ConstCharacter) {
            shape = ((ConstCharacter) jpo).getShape();
        } else if (jpo instanceof ConstCharacterVirtual) {
            shape = ((ConstCharacterVirtual) jpo).getShape();
        } else if (jpo instanceof VehicleConstraint) {
            shape = ((VehicleConstraint) jpo).getVehicleBody().getShape();
        } else {
            String className = jpo.getClass().getSimpleName();
            throw new IllegalArgumentException("class = " + className);
        }

        MeshingStrategy meshingStrategy;
        String programName;
        TextureKey textureKey;

        EShapeSubType subType = shape.getSubType();
        if (subType == EShapeSubType.Plane) {
            meshingStrategy = new MeshingStrategy(
                    0, NormalsOption.Facet,
                    UvsOption.Linear, new Vector4f(uvScale, 0f, 0f, 0f),
                    new Vector4f(0f, 0f, uvScale, 0f)
            );
            programName = "Phong/Distant/Texture";
            textureKey = new TextureKey("procedural:///checkerboard?size=128",
                    Filter.Linear, Filter.NearestMipmapLinear,
                    WrapFunction.Repeat, WrapFunction.Repeat, true,
                    FlipAxes.noFlip, 16f);

        } else if (subType == EShapeSubType.Sphere) {
            meshingStrategy = new MeshingStrategy(
                    -3, NormalsOption.Sphere, UvsOption.Spherical,
                    new Vector4f(uvScale, 0f, 0f, 0f),
                    new Vector4f(0f, uvScale, 0f, 0f)
            );
            programName = "Phong/Distant/Texture";
            textureKey = new TextureKey(
                    "procedural:///checkerboard?size=2&color0=999999ff",
                    Filter.Nearest, Filter.Nearest);

        } else {
            programName = "Phong/Distant/Monochrome";
            textureKey = null;

            if (subType == EShapeSubType.Capsule
                    || subType == EShapeSubType.Cylinder
                    || subType == EShapeSubType.HeightField
                    || subType == EShapeSubType.TaperedCapsule
                    || subType == EShapeSubType.TaperedCylinder) {
                meshingStrategy = new MeshingStrategy("low/Smooth");
            } else {
                meshingStrategy = new MeshingStrategy("low/Facet");
            }
        }

        Geometry geometry;
        if (jpo instanceof ConstBody) {
            ConstBody body = (ConstBody) jpo;
            if (body.isSoftBody()) {
                throw new IllegalArgumentException(jpo.toString()); // TODO
            } else {
                geometry = new RigidBodyShapeGeometry(body, meshingStrategy);
            }

        } else if (jpo instanceof ConstCharacter) {
            ConstCharacter character = (ConstCharacter) jpo;
            geometry = new CharacterShapeGeometry(character, meshingStrategy);

        } else if (jpo instanceof ConstCharacterVirtual) {
            ConstCharacterVirtual character = (CharacterVirtual) jpo;
            geometry = new CharacterVirtualShapeGeometry(
                    character, meshingStrategy);

        } else if (jpo instanceof VehicleConstraint) {
            VehicleConstraint constraint = (VehicleConstraint) jpo;
            ConstBody body = constraint.getVehicleBody();
            geometry = new RigidBodyShapeGeometry(body, meshingStrategy);

        } else {
            throw new IllegalArgumentException(jpo.toString());
        }

        geometry.setProgram(programName);
        geometry.setSpecularColor(Constants.GRAY);
        if (textureKey != null) {
            geometry.setTexture(textureKey);
        }

        return geometry;
    }

    /**
     * Visualize the wheels of the specified vehicle.
     *
     * @param vehicle the vehicle to visualize (not null)
     * @return an array of new, visible geometries
     */
    public static Geometry[] visualizeWheels(VehicleConstraint vehicle) {
        int numWheels = vehicle.countWheels();
        Geometry[] result = new Geometry[numWheels];
        for (int wheelIndex = 0; wheelIndex < numWheels; ++wheelIndex) {
            result[wheelIndex] = new WheelGeometry(vehicle, wheelIndex);
        }

        return result;
    }
    // *************************************************************************
    // BaseApplication methods

    /**
     * Callback invoked after the main update loop terminates.
     */
    @Override
    protected void cleanUp() {
        physicsSystem.removeAllBodies();

        for (Mesh mesh : meshCache.values()) {
            mesh.cleanUp();
        }
        meshCache.clear();
    }

    /**
     * Callback invoked before the main update loop begins. Meant to be
     * overridden.
     */
    @Override
    protected void initialize() {
        this.tempAllocator = new TempAllocatorImpl(150 * 1024 * 1024);
        this.jobSystem = new JobSystemThreadPool(Jolt.cMaxPhysicsJobs,
                Jolt.cMaxPhysicsBarriers, numWorkerThreads);
        this.physicsSystem = createSystem();
        populateSystem();
        physicsSystem.optimizeBroadPhase();
    }

    /**
     * Callback invoked during each iteration of the main update loop. Meant to
     * be overridden.
     */
    @Override
    protected void render() {
        ++renderCount;

        // Advance the physics, but not during the first render().
        long nanoTime = System.nanoTime();
        if (renderCount > 1) {
            long nanoseconds = nanoTime - lastPhysicsUpdate;
            float seconds = 1e-9f * nanoseconds;
            updatePhysics(seconds);
        }
        this.lastPhysicsUpdate = nanoTime;

        cleanUpGeometries();
        super.render();
    }
    // *************************************************************************
    // private methods

    /**
     * Hide any geometries associated with physics objects that are no longer in
     * the PhysicsSystem.
     */
    private void cleanUpGeometries() {
        Collection<Geometry> geometriesToHide
                = new ArrayList<>(); // TODO garbage
        //System.out.println();
        for (Geometry geometry : listVisible()) {
            //System.out.println(
            //       "geometry type=" + geometry.getClass().getSimpleName());
            //System.out.println("  " + geometry.toString());
            if (geometry.wasRemovedFrom(physicsSystem)) {
                geometriesToHide.add(geometry);
            }
        }

        hideAll(geometriesToHide);
    }

    /**
     * Print basic library information to the specified stream during
     * initialization.
     *
     * @param printStream where to print the information (not null)
     */
    private static void printLibraryInfo(PrintStream printStream) {
        printStream.print("Jolt JNI version ");
        printStream.print(Jolt.versionString());
        printStream.print('-');

        String buildType = Jolt.buildType();
        printStream.print(buildType);

        if (Jolt.isDoublePrecision()) {
            printStream.print("Dp");
        } else {
            printStream.print("Sp");
        }

        printStream.println(" initializing...");
        printStream.println();
        printStream.flush();
    }
}
