package com.jcope.util;

import static com.jcope.debug.Debug.assert_;

public class Scale {
    private static boolean scaleWithAspect(final boolean supportScalingUp,
            final float w, final float h, final float wMax, final float hMax,
            final DimensionF outSize) {
        boolean rval;

        assert_(w > 0);
        assert_(h > 0);
        assert_(wMax > 0);
        assert_(hMax > 0);
        assert_(outSize != null);

        if (((w == wMax || ((!supportScalingUp) && w < wMax)) && h <= hMax)
                || (h == hMax && w <= wMax)) {
            outSize.width = w;
            outSize.height = h;

            rval = false;
        } else {
            float wScaled = w, hScaled = h;

            if (wScaled != wMax) {
                wScaled = wMax;
                hScaled = (wScaled * h) / w;
            }
            if (hScaled > hMax) {
                hScaled = hMax;
                wScaled = (hScaled * w) / h;
            }

            // prevent returning scale values less than 1.0f
            if (wScaled < 1.0f) {
                wScaled = 1.0f;
            }
            if (hScaled < 1.0f) {
                hScaled = 1.0f;
            }

            outSize.width = wScaled;
            outSize.height = hScaled;

            rval = true;
        }

        return rval;
    }

    private static void getScaleFactors(boolean supportScalingUp, float w,
            float h, float wMax, float hMax, DimensionF outScaleFactors) {
        if (scaleWithAspect(supportScalingUp, w, h, wMax, hMax, outScaleFactors)) {
            outScaleFactors.width /= w;
            outScaleFactors.height /= h;
        } else {
            outScaleFactors.width = 1.0f;
            outScaleFactors.height = 1.0f;
        }
    }

    public static boolean shrinkToFitWithin(float w, float h, float wMax,
            float hMax, DimensionF outSize) {
        return scaleWithAspect(Boolean.FALSE, w, h, wMax, hMax, outSize);
    }

    public static void factorsThatShrinkToFitWithin(float w, float h,
            float wMax, float hMax, DimensionF outScaleFactors) {
        getScaleFactors(Boolean.FALSE, w, h, wMax, hMax, outScaleFactors);
    }

    public static boolean stretchToFit(float w, float h, float wMax,
            float hMax, DimensionF outSize) {
        return scaleWithAspect(Boolean.TRUE, w, h, wMax, hMax, outSize);
    }

    public static void factorsThatStretchToFit(float w, float h, float wMax,
            float hMax, DimensionF outScaleFactors) {
        getScaleFactors(Boolean.TRUE, w, h, wMax, hMax, outScaleFactors);
    }
}
