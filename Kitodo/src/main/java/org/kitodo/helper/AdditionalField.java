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

package org.kitodo.helper;

import java.util.List;

import javax.faces.model.SelectItem;

import org.apache.commons.lang.StringUtils;

public class AdditionalField {
    private String title;
    private String value = "";
    private boolean required = false;
    private String from = "process";
    private List<SelectItem> selectList;
    private boolean ughbinding = false;
    private String docstruct;
    private String metadata;
    private String isdoctype = "";
    private String isnotdoctype = "";
    private String initStart = ""; // defined in kitodo_projects.xml
    private String initEnd = "";
    private boolean autogenerated = false;
    private String docType;

    public AdditionalField(String docType) {
        this.docType = docType;
    }

    public String getInitStart() {
        return this.initStart;
    }

    /**
     * Set init start.
     *
     * @param newValue
     *            String
     */
    public void setInitStart(String newValue) {
        this.initStart = newValue;
        if (this.initStart == null) {
            this.initStart = "";
        }
        this.value = this.initStart + this.value;
    }

    /**
     * Get init end.
     *
     * @return String
     */
    public String getInitEnd() {
        return this.initEnd;
    }

    /**
     * Set init end.
     *
     * @param newValue
     *            String
     */
    public void setInitEnd(String newValue) {
        this.initEnd = newValue;
        if (this.initEnd == null) {
            this.initEnd = "";
        }
        this.value = this.value + this.initEnd;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getValue() {
        return this.value;
    }

    /**
     * Set value.
     *
     * @param newValue
     *            String
     */
    public void setValue(String newValue) {
        if (newValue == null || newValue.equals(this.initStart)) {
            newValue = "";
        }
        if (newValue.startsWith(this.initStart)) {
            this.value = newValue + this.initEnd;
        } else {
            this.value = this.initStart + newValue + this.initEnd;
        }
    }

    public String getFrom() {
        return this.from;
    }

    /**
     * Set from.
     *
     * @param infrom
     *            input from as String
     */
    public void setFrom(String infrom) {
        if (infrom != null && infrom.length() != 0) {
            this.from = infrom;
        }
    }

    public List<SelectItem> getSelectList() {
        return this.selectList;
    }

    public void setSelectList(List<SelectItem> selectList) {
        this.selectList = selectList;
    }

    public boolean isRequired() {
        return this.required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isUghbinding() {
        return this.ughbinding;
    }

    public void setUghbinding(boolean ughbinding) {
        this.ughbinding = ughbinding;
    }

    public String getDocstruct() {
        return this.docstruct;
    }

    /**
     * Set document structure.
     *
     * @param docstruct
     *            String
     */
    public void setDocstruct(String docstruct) {
        this.docstruct = docstruct;
        if (this.docstruct == null) {
            this.docstruct = "topstruct";
        }
    }

    public String getMetadata() {
        return this.metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getIsdoctype() {
        return this.isdoctype;
    }

    /**
     * Set is document type.
     *
     * @param isdoctype
     *            String
     */
    public void setIsdoctype(String isdoctype) {
        this.isdoctype = isdoctype;
        if (this.isdoctype == null) {
            this.isdoctype = "";
        }
    }

    public String getIsnotdoctype() {
        return this.isnotdoctype;
    }

    /**
     * Set is not document type.
     *
     * @param isnotdoctype
     *            String
     */
    public void setIsnotdoctype(String isnotdoctype) {
        this.isnotdoctype = isnotdoctype;
        if (this.isnotdoctype == null) {
            this.isnotdoctype = "";
        }
    }

    /**
     * Get show depending on document type.
     *
     * @return show
     */
    public boolean getShowDependingOnDoctype() {
        /* wenn nix angegeben wurde, dann anzeigen */
        if (this.isdoctype.equals("") && this.isnotdoctype.equals("")) {
            return true;
        }

        /* wenn pflicht angegeben wurde */
        if (!this.isdoctype.equals("") && !StringUtils.containsIgnoreCase(isdoctype, this.docType)) {
            return false;
        }

        /* wenn nur "darf nicht" angegeben wurde */
        return !(!this.isnotdoctype.equals("") && StringUtils.containsIgnoreCase(isnotdoctype, this.docType));
    }

    /**
     * Set auto generated.
     *
     * @param autogenerated
     *            the autogenerated to set
     */
    public void setAutogenerated(boolean autogenerated) {
        this.autogenerated = autogenerated;
    }

    /**
     * Get auto generated.
     *
     * @return the autogenerated
     */
    public boolean getAutogenerated() {
        return this.autogenerated;
    }
}
