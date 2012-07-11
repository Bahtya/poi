/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

package org.apache.poi.hssf.usermodel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.poi.ddf.*;
import org.apache.poi.hssf.model.DrawingManager2;
import org.apache.poi.hssf.record.EscherAggregate;
import org.apache.poi.ss.usermodel.Chart;
import org.apache.poi.util.StringUtil;
import org.apache.poi.util.Internal;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.ClientAnchor;

/**
 * The patriarch is the toplevel container for shapes in a sheet.  It does
 * little other than act as a container for other shapes and groups.
 *
 * @author Glen Stampoultzis (glens at apache.org)
 */
public final class HSSFPatriarch implements HSSFShapeContainer, Drawing {
    private final List<HSSFShape> _shapes = new ArrayList<HSSFShape>();
//    private int _x1 = 0;
//    private int _y1  = 0 ;
//    private int _x2 = 1023;
//    private int _y2 = 255;

    private final EscherSpgrRecord _spgrRecord;
    private final EscherContainerRecord _mainSpgrContainer;

    /**
     * The EscherAggregate we have been bound to.
     * (This will handle writing us out into records,
     *  and building up our shapes from the records)
     */
    private EscherAggregate _boundAggregate;
	final HSSFSheet _sheet; // TODO make private

    /**
     * Creates the patriarch.
     *
     * @param sheet the sheet this patriarch is stored in.
     */
    HSSFPatriarch(HSSFSheet sheet, EscherAggregate boundAggregate){
        _sheet = sheet;
		_boundAggregate = boundAggregate;
        _mainSpgrContainer = _boundAggregate.getEscherContainer().getChildContainers().get(0);
        EscherContainerRecord spContainer = (EscherContainerRecord) _boundAggregate.getEscherContainer()
                .getChildContainers().get(0).getChild(0);
        _spgrRecord = spContainer.getChildById(EscherSpgrRecord.RECORD_ID);
        buildShapeTree();
    }

    /**
     * remove first level shapes
     * @param shape to be removed
     */
    public void removeShape(HSSFShape shape){
        _mainSpgrContainer.removeChildRecord(shape.getEscherContainer());
        shape.afterRemove(this);
        _shapes.remove(shape);
    }

    public void afterCreate(){
        DrawingManager2 drawingManager = _sheet.getWorkbook().getWorkbook().getDrawingManager();
        short dgId = drawingManager.findNewDrawingGroupId();
        _boundAggregate.setDgId(dgId);
        _boundAggregate.setMainSpRecordId(newShapeId());
        drawingManager.incrementDrawingsSaved();
    }

    /**
     * Creates a new group record stored under this patriarch.
     *
     * @param anchor    the client anchor describes how this group is attached
     *                  to the sheet.
     * @return  the newly created group.
     */
    public HSSFShapeGroup createGroup(HSSFClientAnchor anchor)
    {
        HSSFShapeGroup group = new HSSFShapeGroup(null, anchor);
        group.anchor = anchor;
        addShape(group);
        onCreate(group);
        return group;
    }

    /**
     * Creates a simple shape.  This includes such shapes as lines, rectangles,
     * and ovals.
     *
     * @param anchor    the client anchor describes how this group is attached
     *                  to the sheet.
     * @return  the newly created shape.
     */
    public HSSFSimpleShape createSimpleShape(HSSFClientAnchor anchor)
    {
        HSSFSimpleShape shape = new HSSFSimpleShape(null, anchor);
        shape.anchor = anchor;
        addShape(shape);
        //open existing file
        onCreate(shape);
        EscherSpRecord sp = shape.getEscherContainer().getChildById(EscherSpRecord.RECORD_ID);
        if (shape.anchor.isHorizontallyFlipped()){
            sp.setFlags(sp.getFlags() | EscherSpRecord.FLAG_FLIPHORIZ);
        }
        if (shape.anchor.isVerticallyFlipped()){
            sp.setFlags(sp.getFlags() | EscherSpRecord.FLAG_FLIPVERT);
        }
        return shape;
    }

    /**
     * Creates a picture.
     *
     * @param anchor    the client anchor describes how this group is attached
     *                  to the sheet.
     * @return  the newly created shape.
     */
    public HSSFPicture createPicture(HSSFClientAnchor anchor, int pictureIndex)
    {
        HSSFPicture shape = new HSSFPicture(null, anchor);
        shape.setPictureIndex( pictureIndex );
        shape.anchor = anchor;
        addShape(shape);
        //open existing file
        onCreate(shape);

        EscherSpRecord sp = shape.getEscherContainer().getChildById(EscherSpRecord.RECORD_ID);
        if (shape.anchor.isHorizontallyFlipped()){
            sp.setFlags(sp.getFlags() | EscherSpRecord.FLAG_FLIPHORIZ);
        }
        if (shape.anchor.isVerticallyFlipped()){
            sp.setFlags(sp.getFlags() | EscherSpRecord.FLAG_FLIPVERT);
        }
        return shape;
    }

    public HSSFPicture createPicture(ClientAnchor anchor, int pictureIndex)
    {
        return createPicture((HSSFClientAnchor)anchor, pictureIndex);
    }

    /**
     * Creates a polygon
     *
     * @param anchor    the client anchor describes how this group is attached
     *                  to the sheet.
     * @return  the newly created shape.
     */
    public HSSFPolygon createPolygon(HSSFClientAnchor anchor)
    {
        HSSFPolygon shape = new HSSFPolygon(null, anchor);
        shape.anchor = anchor;
        addShape(shape);
        onCreate(shape);
        return shape;
    }

    /**
     * Constructs a textbox under the patriarch.
     *
     * @param anchor    the client anchor describes how this group is attached
     *                  to the sheet.
     * @return      the newly created textbox.
     */
    public HSSFTextbox createTextbox(HSSFClientAnchor anchor)
    {
        HSSFTextbox shape = new HSSFTextbox(null, anchor);
        shape.anchor = anchor;
        addShape(shape);
        onCreate(shape);
        return shape;
    }

    /**
     * Constructs a cell comment.
     *
     * @param anchor    the client anchor describes how this comment is attached
     *                  to the sheet.
     * @return      the newly created comment.
     */
   public HSSFComment createComment(HSSFAnchor anchor)
    {
        HSSFComment shape = new HSSFComment(null, anchor);
        shape.anchor = anchor;
        addShape(shape);
        onCreate(shape);
        return shape;
    }

    /**
     * YK: used to create autofilters
     *
     * @see org.apache.poi.hssf.usermodel.HSSFSheet#setAutoFilter(org.apache.poi.ss.util.CellRangeAddress)
     */
     HSSFSimpleShape createComboBox(HSSFAnchor anchor)
     {
         HSSFSimpleShape shape = new HSSFSimpleShape(null, anchor);
         shape.setShapeType(HSSFSimpleShape.OBJECT_TYPE_COMBO_BOX);
         shape.anchor = anchor;
         addShape(shape);
         return shape;
     }

    public HSSFComment createCellComment(ClientAnchor anchor) {
        return createComment((HSSFAnchor)anchor);
    }

    /**
     * Returns a list of all shapes contained by the patriarch.
     */
    public List<HSSFShape> getChildren()
    {
        return _shapes;
    }

    /**
     * add a shape to this drawing
     */
    @Internal
    public void addShape(HSSFShape shape){
        shape._patriarch = this;
        _shapes.add(shape);
    }

    private void onCreate(HSSFShape shape){
        if(_boundAggregate.getPatriarch() == null){
            EscherContainerRecord spgrContainer =
                    _boundAggregate.getEscherContainer().getChildContainers().get(0);

            EscherContainerRecord spContainer = shape.getEscherContainer();
            int shapeId = newShapeId();
            shape.setShapeId(shapeId);

            spgrContainer.addChildRecord(spContainer);
            shape.afterInsert(this);
        }
    }

    /**
     * Total count of all children and their children's children.
     */
    public int countOfAllChildren() {
        int count = _shapes.size();
        for (Iterator<HSSFShape> iterator = _shapes.iterator(); iterator.hasNext();) {
            HSSFShape shape = iterator.next();
            count += shape.countOfAllChildren();
        }
        return count;
    }
    /**
     * Sets the coordinate space of this group.  All children are constrained
     * to these coordinates.
     */
    public void setCoordinates(int x1, int y1, int x2, int y2){
        _spgrRecord.setRectY1(y1);
        _spgrRecord.setRectY2(y2);
        _spgrRecord.setRectX1(x1);
        _spgrRecord.setRectX2(x2);
    }

    int newShapeId() {
        DrawingManager2 dm = _sheet.getWorkbook().getWorkbook().getDrawingManager();
        EscherDgRecord dg =
                _boundAggregate.getEscherContainer().getChildById(EscherDgRecord.RECORD_ID);
        short drawingGroupId = dg.getDrawingGroupId();
        return dm.allocateShapeId(drawingGroupId, dg);
    }

    /**
     * Does this HSSFPatriarch contain a chart?
     * (Technically a reference to a chart, since they
     *  get stored in a different block of records)
     * FIXME - detect chart in all cases (only seems
     *  to work on some charts so far)
     */
    public boolean containsChart() {
        // TODO - support charts properly in usermodel

        // We're looking for a EscherOptRecord
        EscherOptRecord optRecord = (EscherOptRecord)
            _boundAggregate.findFirstWithId(EscherOptRecord.RECORD_ID);
        if(optRecord == null) {
            // No opt record, can't have chart
            return false;
        }

        for(Iterator<EscherProperty> it = optRecord.getEscherProperties().iterator(); it.hasNext();) {
            EscherProperty prop = it.next();
            if(prop.getPropertyNumber() == 896 && prop.isComplex()) {
                EscherComplexProperty cp = (EscherComplexProperty)prop;
                String str = StringUtil.getFromUnicodeLE(cp.getComplexData());

                if(str.equals("Chart 1\0")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * The top left x coordinate of this group.
     */
    public int getX1()
    {
        return _spgrRecord.getRectX1();
    }

    /**
     * The top left y coordinate of this group.
     */
    public int getY1()
    {
        return _spgrRecord.getRectY1();
    }

    /**
     * The bottom right x coordinate of this group.
     */
    public int getX2()
    {
        return _spgrRecord.getRectX2();
    }

    /**
     * The bottom right y coordinate of this group.
     */
    public int getY2()
    {
        return _spgrRecord.getRectY2();
    }

    /**
     * Returns the aggregate escher record we're bound to
     */
    protected EscherAggregate _getBoundAggregate() {
        return _boundAggregate;
    }

    /**
     * Creates a new client anchor and sets the top-left and bottom-right
     * coordinates of the anchor.
     *
     * @param dx1  the x coordinate in EMU within the first cell.
     * @param dy1  the y coordinate in EMU within the first cell.
     * @param dx2  the x coordinate in EMU within the second cell.
     * @param dy2  the y coordinate in EMU within the second cell.
     * @param col1 the column (0 based) of the first cell.
     * @param row1 the row (0 based) of the first cell.
     * @param col2 the column (0 based) of the second cell.
     * @param row2 the row (0 based) of the second cell.
     * @return the newly created client anchor
     */
    public HSSFClientAnchor createAnchor(int dx1, int dy1, int dx2, int dy2, int col1, int row1, int col2, int row2){
        return new HSSFClientAnchor(dx1, dy1, dx2, dy2, (short)col1, row1, (short)col2, row2);
    }

	public Chart createChart(ClientAnchor anchor) {
		throw new RuntimeException("NotImplemented");
	}


    void buildShapeTree(){
        EscherContainerRecord dgContainer = _boundAggregate.getEscherContainer();
        if (dgContainer == null){
            return;
        }
        EscherContainerRecord spgrConrainer = dgContainer.getChildContainers().get(0);
        List<EscherContainerRecord> spgrChildren = spgrConrainer.getChildContainers();

        for(int i = 0; i < spgrChildren.size(); i++){
            EscherContainerRecord spContainer = spgrChildren.get(i);
            if (i == 0){
                continue;
            } else {
                HSSFShapeFactory.createShapeTree(spContainer, _boundAggregate, this);
            }
        }
    }
}
