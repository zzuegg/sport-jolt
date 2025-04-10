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

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.readonly.ConstBody;
import com.github.stephengold.joltjni.readonly.ConstBodyId;
import com.github.stephengold.joltjni.readonly.ConstCharacter;
import com.github.stephengold.joltjni.readonly.ConstCharacterVirtual;
import com.github.stephengold.joltjni.readonly.ConstJoltPhysicsObject;
import com.github.stephengold.joltjni.readonly.QuatArg;
import com.github.stephengold.joltjni.readonly.RMat44Arg;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import com.github.stephengold.sportjolt.BaseApplication;
import com.github.stephengold.sportjolt.Constants;
import com.github.stephengold.sportjolt.Geometry;
import com.github.stephengold.sportjolt.Mesh;
import com.github.stephengold.sportjolt.Validate;
import com.github.stephengold.sportjolt.mesh.ArrowMesh;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4fc;

/**
 * Visualize one of the local axes of a physics object or else a "floating"
 * arrow.
 */
public class LocalAxisGeometry extends Geometry {
    // *************************************************************************
    // constants

    /**
     * map axis indices to colors
     */
    final private static Vector4fc[] colors = {
        Constants.RED, // X
        Constants.GREEN, // Y
        Constants.BLUE // Z
    };
    // *************************************************************************
    // fields

    /**
     * physics object to visualize
     */
    final private ConstJoltPhysicsObject jpo;
    /**
     * length of the axis (in world units)
     */
    final private float length;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a Geometry to visualize the specified local axis of the
     * specified physics object and make the Geometry visible.
     *
     * @param jpo the physics object (alias created) or null for a "floating"
     * axis
     * @param axisIndex which axis: 0&rarr;X, 1&rarr;Y, 2&rarr;Z
     * @param length the length of the axis (in world units, &ge;0)
     */
    public LocalAxisGeometry(
            ConstJoltPhysicsObject jpo, int axisIndex, float length) {
        super();
        Validate.axisIndex(axisIndex, "axisIndex");
        Validate.nonNegative(length, "length");
        assert jpo == null || jpo instanceof ConstBody
                || jpo instanceof ConstCharacter;

        this.jpo = jpo;
        this.length = length;

        Vector4fc color = colors[axisIndex];
        super.setColor(color);

        Mesh mesh = ArrowMesh.getMesh(axisIndex);
        super.setMesh(mesh);

        super.setProgram("Unshaded/Monochrome");

        BaseApplication.makeVisible(this);
    }
    // *************************************************************************
    // Geometry methods

    /**
     * Update properties based on the physics object and then render.
     */
    @Override
    public void updateAndRender() {
        updateTransform();
        super.updateAndRender();
    }

    /**
     * Test whether the physics object has been removed from the specified
     * PhysicsSystem.
     *
     * @param system the system to test (not null, unaffected)
     * @return {@code true} if removed, otherwise {@code false}
     */
    @Override
    public boolean wasRemovedFrom(PhysicsSystem system) {
        boolean result;
        if (jpo == null) {
            result = false;

        } else if (jpo instanceof ConstBody) {
            ConstBody body = (ConstBody) jpo;
            BodyInterface bi = system.getBodyInterface();
            ConstBodyId id = body.getId();
            result = !bi.isAdded(id);

        } else if (jpo instanceof ConstCharacter) {
            result = false; // TODO

        } else {
            throw new IllegalStateException(jpo.getClass().getSimpleName());
        }

        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Update the mesh-to-world transform.
     */
    private void updateTransform() {
        if (jpo == null) {
            // no change to transform

        } else if (jpo instanceof ConstBody) {
            ConstBody body = (ConstBody) jpo;
            RMat44Arg matrix = body.getCenterOfMassTransform();

            RVec3Arg location = matrix.getTranslation();
            setLocation(new Vector3f(location.x(), location.y(), location.z()));

            QuatArg orientation = matrix.getQuaternion();
            setOrientation(new Quaternionf(orientation.getX(),orientation.getY(),orientation.getZ(),orientation.getW()));

        } else if (jpo instanceof ConstCharacter) {
            ConstCharacter character = (ConstCharacter) jpo;
            RVec3Arg location = character.getCenterOfMassPosition();
            setLocation(new Vector3f(location.x(), location.y(), location.z()));

            QuatArg orientation = character.getRotation();
            setOrientation(new Quaternionf(orientation.getX(),orientation.getY(),orientation.getZ(),orientation.getW()));

        } else if (jpo instanceof ConstCharacterVirtual) {
            ConstCharacterVirtual character = (ConstCharacterVirtual) jpo;
            RVec3Arg location = character.getCenterOfMassPosition();
            setLocation(new Vector3f(location.x(), location.y(), location.z()));

            QuatArg orientation = character.getRotation();
            setOrientation(new Quaternionf(orientation.getX(),orientation.getY(),orientation.getZ(),orientation.getW()));

        } else {
            throw new IllegalStateException(jpo.getClass().getSimpleName());
        }

        setScale(length);
    }
}
