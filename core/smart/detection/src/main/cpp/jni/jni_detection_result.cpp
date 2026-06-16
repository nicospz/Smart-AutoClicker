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

#include "jni.hpp"
#include "../detector/matching/template_matching_result.hpp"

void setDetectionResult(JNIEnv *env, jobject self, TemplateMatchingResult* result) {
    jclass cls = env->GetObjectClass(self);
    if (!cls)
        env->FatalError("GetObjectClass failed");

    jmethodID methodId = env->GetMethodID(cls, "setResults", "(ZIID)V");

    env->CallVoidMethod(self, methodId,
                        result->isDetected(),
                        (int) result->getResultAreaCenterX(), (int) result->getResultAreaCenterY(),
                        result->getResultConfidence());
}

jobject createDetectionResultsList(JNIEnv *env, const std::vector<TemplateMatchingResult>& results) {
    jclass arrayListCls = env->FindClass("java/util/ArrayList");
    if (!arrayListCls) env->FatalError("FindClass ArrayList failed");

    jmethodID arrayListCtor = env->GetMethodID(arrayListCls, "<init>", "()V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListCls, "add", "(Ljava/lang/Object;)Z");
    if (!arrayListCtor || !arrayListAdd) env->FatalError("ArrayList method lookup failed");

    jobject list = env->NewObject(arrayListCls, arrayListCtor);

    jclass resultCls = env->FindClass("com/buzbuz/smartautoclicker/core/detection/DetectionResult");
    if (!resultCls) env->FatalError("FindClass DetectionResult failed");

    jmethodID resultCtor = env->GetMethodID(resultCls, "<init>", "()V");
    if (!resultCtor) env->FatalError("DetectionResult constructor lookup failed");

    for (auto result : results) {
        jobject resultObject = env->NewObject(resultCls, resultCtor);
        setDetectionResult(env, resultObject, &result);
        env->CallBooleanMethod(list, arrayListAdd, resultObject);
        env->DeleteLocalRef(resultObject);
    }

    env->DeleteLocalRef(resultCls);
    env->DeleteLocalRef(arrayListCls);
    return list;
}
