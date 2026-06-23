/*
 * Copyright (C) 2010 Craig Chamberlain, Creative Workz.
 * BSD 3-Clause License — vendored from Tasker developer documentation.
 */
package net.dinglisch.android.tasker;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.PatternMatcher;

import java.util.List;

public class TaskerIntent {

    public static final String ACTION_RUN_TASK = "net.dinglisch.android.tasker.ACTION_TASK";
    public static final String ACTION_TASK_COMPLETE = "net.dinglisch.android.tasker.ACTION_TASK_COMPLETE";
    public static final String ACTION_OPEN_EXTERNAL_ACCESS = "net.dinglisch.android.tasker.ACTION_OPEN_EXTERNAL_ACCESS";

    public static final String EXTRA_TASK_NAME = "task_name";
    public static final String EXTRA_SUCCESS_FLAG = "success";
    public static final String EXTRA_VAR_NAMES_LIST = "varNames";
    public static final String EXTRA_VAR_VALUES_LIST = "varValues";

    public static final String TASK_NAME_DATA_SCHEME = "task";

    public static final String PROVIDER_URI_TASKS = "content://net.dinglisch.android.tasker/tasks";
    public static final String PROVIDER_URI_PREFS = "content://net.dinglisch.android.tasker/prefs";
    public static final String PROVIDER_COL_NAME = "name";
    public static final String PROVIDER_COL_PROJECT_NAME = "project_name";
    public static final String PROVIDER_COL_NAME_EXTERNAL_ACCESS = "ext_access";

    public enum Status {
        OK,
        NOT_INSTALLED,
        NO_ACCESS,
    }

    private final Intent intent;

    public TaskerIntent(String taskName) {
        intent = new Intent(ACTION_RUN_TASK);
        intent.putExtra(EXTRA_TASK_NAME, taskName);
    }

    public Intent getIntent() {
        return intent;
    }

    public IntentFilter getCompletionFilter() {
        return getCompletionFilter(intent.getStringExtra(EXTRA_TASK_NAME));
    }

    public static IntentFilter getCompletionFilter(String taskName) {
        IntentFilter filter = new IntentFilter(ACTION_TASK_COMPLETE);
        filter.addDataScheme(TASK_NAME_DATA_SCHEME);
        filter.addDataPath(taskName, PatternMatcher.PATTERN_LITERAL);
        return filter;
    }

    public static Status testStatus(Context context) {
        if (!isInstalled(context)) return Status.NOT_INSTALLED;
        if (!havePermission(context)) return Status.NO_ACCESS;
        return Status.OK;
    }

    public static boolean isInstalled(Context context) {
        List<ResolveInfo> receivers = context.getPackageManager()
                .queryBroadcastReceivers(new Intent(ACTION_RUN_TASK), 0);
        return receivers != null && !receivers.isEmpty();
    }

    public static boolean havePermission(Context context) {
        return context.checkCallingOrSelfPermission("net.dinglisch.android.tasker.PERMISSION_RUN_TASKS")
                == PackageManager.PERMISSION_GRANTED;
    }

    public static Intent getExternalAccessPrefsIntent() {
        return new Intent(ACTION_OPEN_EXTERNAL_ACCESS);
    }
}
