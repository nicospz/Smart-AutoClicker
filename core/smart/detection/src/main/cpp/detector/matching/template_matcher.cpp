/*
 * Copyright (C) 2025 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#include <opencv2/imgproc/imgproc.hpp>

#include "template_matcher.hpp"
#include "../../logs/log.h"
#include "../../utils/roi.h"


using namespace smartautoclicker;


void TemplateMatcher::reset() {
    currentMatchingResult.reset();
}

TemplateMatchingResult *TemplateMatcher::getMatchingResults() {
    return &currentMatchingResult;
}

void TemplateMatcher::matchTemplate(
        const ScreenImage& screenImage,
        const ConditionImage& condition,
        const cv::Rect& detectionArea,
        int threshold
) {

    // Crop the gray screen image to get only the detection area
    cv::Mat screenCroppedGrayMat = screenImage.cropGray(detectionArea);
    if (screenCroppedGrayMat.empty()) {
        LOGE("TemplateMatcher", "screenCroppedGrayMat is empty after cropping.");
        return;
    }

    if (screenCroppedGrayMat.rows < condition.getGrayMat()->rows ||
        screenCroppedGrayMat.cols < condition.getGrayMat()->cols) {
        LOGE("TemplateMatcher", "Detection area is smaller than condition image.");
        return;
    }

    // Initialize result mat
    cv::Mat newResultsMat = cv::Mat(
            screenCroppedGrayMat.rows - condition.getGrayMat()->rows + 1,
            screenCroppedGrayMat.cols - condition.getGrayMat()->cols + 1,
            CV_32F);

    try {
        // Run OpenCv template matching
        cv::matchTemplate(
                screenCroppedGrayMat,
                *condition.getGrayMat(),
                newResultsMat,
                cv::TM_CCOEFF_NORMED);
    } catch (const cv::Exception& e) {
        LOGE("TemplateMatcher", "OpenCV Exception caught: %s", e.what());
        throw;
    } catch (const std::exception& e) {
        LOGE("TemplateMatcher", "Standard Exception caught: %s", e.what());
        throw; // Rethrow
    } catch (...) {
        LOGE("TemplateMatcher", "Unknown exception caught!");
        throw std::runtime_error("Unknown exception in TemplateMatcher");
    } // Rethrow the Exceptions to be caught by the JNI wrapper

    // Parse result Mat to check for matching
    parseMatchingResult(screenImage, condition, detectionArea, threshold, newResultsMat);
}


std::vector<TemplateMatchingResult> TemplateMatcher::matchTemplateOccurrences(
        const ScreenImage& screenImage,
        const ConditionImage& condition,
        const cv::Rect& detectionArea,
        int threshold,
        int maxResults
) {
    std::vector<TemplateMatchingResult> results;
    if (maxResults <= 0) return results;

    cv::Mat screenCroppedGrayMat = screenImage.cropGray(detectionArea);
    if (screenCroppedGrayMat.empty()) {
        LOGE("TemplateMatcher", "screenCroppedGrayMat is empty after cropping occurrences.");
        return results;
    }

    if (screenCroppedGrayMat.rows < condition.getGrayMat()->rows ||
        screenCroppedGrayMat.cols < condition.getGrayMat()->cols) {
        LOGE("TemplateMatcher", "Detection area is smaller than condition image for occurrences.");
        return results;
    }

    cv::Mat newResultsMat = cv::Mat(
            screenCroppedGrayMat.rows - condition.getGrayMat()->rows + 1,
            screenCroppedGrayMat.cols - condition.getGrayMat()->cols + 1,
            CV_32F);

    try {
        cv::matchTemplate(
                screenCroppedGrayMat,
                *condition.getGrayMat(),
                newResultsMat,
                cv::TM_CCOEFF_NORMED);
    } catch (const cv::Exception& e) {
        LOGE("TemplateMatcher", "OpenCV Exception caught: %s", e.what());
        throw;
    } catch (const std::exception& e) {
        LOGE("TemplateMatcher", "Standard Exception caught: %s", e.what());
        throw;
    } catch (...) {
        LOGE("TemplateMatcher", "Unknown exception caught!");
        throw std::runtime_error("Unknown exception in TemplateMatcher");
    }

    while ((int) results.size() < maxResults) {
        TemplateMatchingResult candidate;
        candidate.reset();
        candidate.updateResults(detectionArea, *condition.getGrayMat(), newResultsMat);

        if (!isConfidenceValid(candidate.getResultConfidence(), threshold)) break;

        candidate.invalidateCurrentResult(*condition.getGrayMat(), newResultsMat);

        if (!isRoiBiggerOrEquals(screenImage.getRoi(), candidate.getResultArea())) continue;

        cv::Mat fullSizeColorCroppedCurrentImage = screenImage.cropColor(candidate.getResultArea());
        double colorDiff = getColorDiff(fullSizeColorCroppedCurrentImage, condition.getColorMean());
        if (colorDiff >= threshold) continue;

        candidate.markResultAsDetected();
        results.push_back(candidate);
    }

    return results;
}

void TemplateMatcher::parseMatchingResult(
        const ScreenImage& screenImage,
        const ConditionImage& condition,
        const cv::Rect& detectionArea,
        int threshold,
        cv::Mat& matchingResult
) {

    while (!currentMatchingResult.isDetected()) {

        // Mark previous results as invalid, if any
        if (!currentMatchingResult.getResultArea().empty()) {
            currentMatchingResult.invalidateCurrentResult(
                    *condition.getGrayMat(),
                    matchingResult);
        }

        // Look for new best match
        currentMatchingResult.updateResults(
                detectionArea,
                *condition.getGrayMat(),
                matchingResult);

        // Check if the highest result is above threshold. If not, we will never find.
        if (!isConfidenceValid(currentMatchingResult.getResultConfidence(), threshold)) break;

        // Check if result area is valid. If not, check next possible match
        if (!isRoiBiggerOrEquals(screenImage.getRoi(), currentMatchingResult.getResultArea())) continue;

        // Check if the colors are matching in the candidate area.
        cv::Mat fullSizeColorCroppedCurrentImage = screenImage.cropColor(currentMatchingResult.getResultArea());
        double colorDiff = getColorDiff(fullSizeColorCroppedCurrentImage,condition.getColorMean());

        // If the colors are OK, the result is valid
        if (colorDiff < threshold) currentMatchingResult.markResultAsDetected();
    }
}

bool TemplateMatcher::isConfidenceValid(double confidence, int threshold) {
    return confidence > ((100.0 - threshold) / 100.0);
}

double TemplateMatcher::getColorDiff(const cv::Mat& image, const cv::Scalar& conditionColorMeans) {
   auto imageColorMeans = mean(image);

   double diff = 0;
   for (int i = 0; i < 3; i++) {
       diff += abs(imageColorMeans.val[i] - conditionColorMeans.val[i]);
   }
   return (diff * 100) / (255 * 3);
}
