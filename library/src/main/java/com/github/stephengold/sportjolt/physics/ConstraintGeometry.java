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

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraintRef;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.operator.Op;
import com.github.stephengold.joltjni.readonly.ConstBody;
import com.github.stephengold.joltjni.readonly.ConstTwoBodyConstraint;
import com.github.stephengold.joltjni.readonly.Mat44Arg;
import com.github.stephengold.joltjni.readonly.QuatArg;
import com.github.stephengold.joltjni.readonly.RMat44Arg;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import com.github.stephengold.sportjolt.BaseApplication;
import com.github.stephengold.sportjolt.Constants;
import com.github.stephengold.sportjolt.Geometry;
import com.github.stephengold.sportjolt.Mesh;
import com.github.stephengold.sportjolt.Validate;
import com.github.stephengold.sportjolt.mesh.ArrowMesh;
import org.joml.Vector4fc;

/**
 * Visualize one end of a {@code TwoBodyConstraint}, using a colored arrow from
 * the center of mass to the pivot.
 *
 * @author Stephen Gold sgold@sonic.net
 */
public class ConstraintGeometry extends Geometry {
    // *************************************************************************
    // fields

    /**
     * end to visualize (1 or 2)
     */
    final private int end;
    /**
     * constraint to visualize
     */
    final private TwoBodyConstraintRef constraint;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a Geometry to visualize the specified end of the specified
     * constraint and make the Geometry visible.
     *
     * @param constraint the constraint to visualize (not null, alias created)
     * @param end which end to visualize (1 or 2)
     */
    public ConstraintGeometry(TwoBodyConstraint constraint, int end) {
        super();
        Validate.nonNull(constraint, "constraint");
        Validate.inRange(end, "end", 1, 2);

        Vector4fc color = (end == 1) ? Constants.GREEN : Constants.RED;
        super.setColor(color);

        Mesh mesh = ArrowMesh.getMesh(Jolt.SWIZZLE_Z);
        super.setMesh(mesh);
        super.setProgram("Unshaded/Monochrome");

        this.constraint = constraint.toRef();
        this.end = end;

        BaseApplication.makeVisible(this);
    }
    // *************************************************************************
    // Geometry methods

    /**
     * Update properties based on the constraint and then render.
     */
    @Override
    public void updateAndRender() {
        ConstBody body;
        Mat44Arg pivotToCom;
        if (end == 1) {
            body = constraint.getBody1();
            pivotToCom = constraint.getConstraintToBody1Matrix();
        } else if (end == 2) {
            body = constraint.getBody2();
            pivotToCom = constraint.getConstraintToBody2Matrix();
        } else {
            throw new RuntimeException();
        }
        RMat44Arg comToWorld = body.getCenterOfMassTransform();
        RVec3Arg com = comToWorld.getTranslation();
        RMat44Arg pivotToWorld = Op.star(comToWorld, pivotToCom);
        RVec3Arg pivot = pivotToWorld.getTranslation();
        RVec3Arg offset = Op.minus(pivot, com);
        float length = (float) offset.length();
        setScale(length);

        if (length > 0f) {
            Vec3 direction = offset.toVec3();
            direction.scaleInPlace(1f / length);
            QuatArg rotation = Quat.sFromTo(Vec3.sAxisZ(), direction);
            setOrientation(rotation);
        }

        RVec3Arg location = comToWorld.getTranslation();
        setLocation(location);

        super.updateAndRender();
    }

    /**
     * Test whether the Constraint has been removed from the specified
     * PhysicsSystem.
     *
     * @param system the system to test (not null, unaffected)
     * @return {@code true} if removed, otherwise {@code false}
     */
    @Override
    public boolean wasRemovedFrom(PhysicsSystem system) {
        ConstTwoBodyConstraint cc = constraint.getPtr();
        boolean result = !system.containsConstraint(cc);

        return result;
    }
}
