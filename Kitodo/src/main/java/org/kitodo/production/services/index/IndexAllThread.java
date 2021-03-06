/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.production.services.index;

import org.kitodo.production.enums.ObjectType;
import org.kitodo.production.helper.Helper;
import org.omnifaces.cdi.PushContext;

public class IndexAllThread extends Thread {

    private PushContext context;
    private IndexingService indexingService;

    IndexAllThread(PushContext pushContext, IndexingService service) {
        context = pushContext;
        indexingService = service;
    }

    @Override
    public void run() {
        indexingService.setIndexingAll(true);

        for (ObjectType objectType : ObjectType.getIndexableObjectTypes()) {
            indexingService.startIndexing(objectType, context);
        }

        try {
            sleep(IndexingService.PAUSE);
        } catch (InterruptedException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), IndexingService.getLogger(), e);
            Thread.currentThread().interrupt();
        }

        indexingService.resetCurrentIndexState();
        indexingService.setIndexingAll(false);

        context.send(IndexingService.INDEXING_FINISHED_MESSAGE);
    }
}
