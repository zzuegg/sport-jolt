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
package com.github.stephengold.sportjolt.mesh;

import com.github.stephengold.sportjolt.Mesh;
import com.github.stephengold.sportjolt.Topology;
import com.github.stephengold.sportjolt.Validate;

/**
 * A LineList mesh that renders crosshairs in the X-Y plane.
 *
 * @author Stephen Gold sgold@sonic.net
 */
public class CrosshairsMesh extends Mesh {
    // *************************************************************************
    // constructors

    /**
     * Instantiate a pair of equal-length crosshairs at the mesh origin.
     *
     * @param size the length of each line (in mesh units, &ge;0)
     */
    public CrosshairsMesh(float size) {
        this(size, size);
    }

    /**
     * Instantiate a pair of axis-aligned crosshairs at the origin.
     *
     * @param width the length of the X-axis line (in mesh units, &ge;0)
     * @param height the length of the Y-axis line (in mesh units, &ge;0)
     */
    public CrosshairsMesh(float width, float height) {
        super(Topology.LineList, 4);
        Validate.nonNegative(width, "width");
        Validate.nonNegative(height, "height");

        super.setPositions(
                -0.5f * width, 0f, 0f,
                +0.5f * width, 0f, 0f,
                0f, -0.5f * height, 0f,
                0f, +0.5f * height, 0f);
    }
}
