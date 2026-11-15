package org.dnsmanager.app.web;

/**
 * What the app does when a page can't be loaded (no connection, site down, …).
 *
 * <p>Instead of Chromium's raw "Webpage not available" page, the tab shows a black screen with the
 * app's icon and "Tap to retry" (the same look as an unloaded tab, ui/UnloadedPlaceholder).</p>
 *
 * <p>Only the <i>main page</i> counts (MainActivity checks {@code request.isForMainFrame()}): when
 * only a picture or script of a page fails, the page itself is there. Plain Java, so LoadErrorsTest
 * checks it on the computer.</p>
 */
public final class LoadErrors {

    private LoadErrors() {
    }

    /**
     * One tab's retry screen (kept on the tab's WebView in TabManager, so it goes away with the tab).
     *
     * <p>Overriding onReceivedError doesn't stop WebView from drawing its own grey "Webpage not available"
     * page; the black screen only covers it. So the screen must stay until a <i>new</i> page is actually
     * drawn without an error: hiding it when a retry starts showed the grey page for as long as a slow retry
     * took. WebView's order, read in Chromium's source
     * (AwContentsClientBridge.onReceivedError, AwWebContentsObserver): onPageStarted → onReceivedError →
     * onPageFinished for a failed page, and onPageCommitVisible once a committed page (error pages too) is
     * drawn. A cancelled load is never committed, so it never counts as shown.</p>
     */
    public static final class TabState {
        private String failedUrl;      // null = no retry screen; "" = failed, address unknown
        private boolean failedThisLoad; // the load that started last failed

        /** onPageStarted: a new load began. */
        public void pageStarted() {
            failedThisLoad = false;
        }

        /** onReceivedError for the page itself. */
        public void failed(String url) {
            failedUrl = url != null ? url : "";
            failedThisLoad = true;
        }

        /**
         * onPageCommitVisible: a page is drawn.
         *
         * @return true if that removed the retry screen (the screen must be updated)
         */
        public boolean pageVisible() {
            if (failedUrl == null || failedThisLoad) {
                return false;
            }
            failedUrl = null;
            return true;
        }

        public boolean showsRetry() {
            return failedUrl != null;
        }

        /** The address that failed ("" if unknown); null while there is no retry screen. */
        public String failedUrl() {
            return failedUrl;
        }
    }
}
