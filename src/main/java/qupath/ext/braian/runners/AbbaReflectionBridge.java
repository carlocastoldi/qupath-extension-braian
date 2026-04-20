// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.runners;

import qupath.lib.images.ImageData;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;

final class AbbaReflectionBridge {
    private static final String[] ATLAS_TOOL_CLASSES = {
            "qupath.ext.biop.abba.AtlasTools",
            "ch.epfl.biop.qupath.atlas.allen.api.AtlasTools"
    };
    private static final String METHOD_NAME = "loadWarpedAtlasAnnotations";
    private static volatile boolean resolved = false;
    private static Method loadMethod;
    private static String failureReason;

    private AbbaReflectionBridge() {
    }

    static boolean isAvailable() {
        resolve();
        return loadMethod != null;
    }

    static String getFailureReason() {
        resolve();
        if (failureReason != null) {
            return failureReason;
        }
        return "ABBA extension is not available.";
    }

    static void loadWarpedAtlasAnnotations(ImageData<BufferedImage> imageData,
            String atlasName,
            String namingProperty,
            boolean splitLeftRight,
            boolean overwrite) {
        resolve();
        if (loadMethod == null) {
            throw new IllegalStateException(getFailureReason());
        }
        try {
            int paramCount = loadMethod.getParameterCount();
            if (paramCount == 3) {
                loadMethod.invoke(null, imageData, namingProperty, splitLeftRight);
            } else if (paramCount == 5) {
                String resolvedAtlas = (atlasName == null || atlasName.isBlank()) ? "allen_mouse_10um_java" : atlasName;
                loadMethod.invoke(null, imageData, resolvedAtlas, namingProperty, splitLeftRight, overwrite);
            } else {
                throw new IllegalStateException("Unsupported ABBA AtlasTools signature");
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("ABBA import failed: " + e.getMessage(), e);
        }
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        synchronized (AbbaReflectionBridge.class) {
            if (resolved) {
                return;
            }
            for (String className : ATLAS_TOOL_CLASSES) {
                try {
                    Class<?> atlasTools = Class.forName(className);
                    Method method = findMethod(atlasTools);
                    if (method != null) {
                        loadMethod = method;
                        failureReason = null;
                        resolved = true;
                        return;
                    }
                    failureReason = "ABBA AtlasTools method not found in " + className + ".";
                } catch (ClassNotFoundException e) {
                    failureReason = "ABBA extension is not installed.";
                }
            }
            resolved = true;
        }
    }

    private static Method findMethod(Class<?> atlasTools) {
        try {
            return atlasTools.getMethod(METHOD_NAME, ImageData.class, String.class, boolean.class);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            return atlasTools.getMethod(METHOD_NAME, ImageData.class, String.class, String.class, boolean.class,
                    boolean.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
