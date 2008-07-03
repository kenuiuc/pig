/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pig.test;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.data.BagFactory;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.DataType;
import org.apache.pig.data.DefaultTuple;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;
import org.apache.pig.impl.physicalLayer.PhysicalOperator;
import org.apache.pig.impl.physicalLayer.POStatus;
import org.apache.pig.impl.physicalLayer.Result;
import org.apache.pig.impl.physicalLayer.plans.PhysicalPlan;
import org.apache.pig.impl.physicalLayer.relationalOperators.POFilter;
import org.apache.pig.impl.physicalLayer.relationalOperators.PORead;
import org.apache.pig.impl.physicalLayer.expressionOperators.*;
import org.apache.pig.test.utils.GenPhyOp;
import org.apache.pig.test.utils.GenRandomData;
import org.apache.pig.test.utils.TestHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestFilter extends junit.framework.TestCase {
    POFilter pass;

    POFilter fail;

    Tuple t;

    DataBag inp;

    POFilter projFil;

    @Before
    public void setUp() throws Exception {
        Random r = new Random();
        pass = GenPhyOp.topFilterOpWithExPlan(50, 25);
        fail = GenPhyOp.topFilterOpWithExPlan(25, 50);
        inp = GenRandomData.genRandSmallTupDataBag(r, 10, 100);
        t = GenRandomData.genRandSmallBagTuple(r, 10, 100);
        projFil = GenPhyOp.topFilterOpWithProj(1, 50);
        POProject inpPrj = GenPhyOp.exprProject();
        Tuple tmpTpl = new DefaultTuple();
        tmpTpl.append(inp);
        inpPrj.setColumn(0);
        inpPrj.setResultType(DataType.TUPLE);
        inpPrj.setOverloaded(true);
        List<PhysicalOperator> inputs = new ArrayList<PhysicalOperator>();
        inputs.add(inpPrj);
        projFil.setInputs(inputs);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testGetNextTuple() throws ExecException, IOException {
        pass.attachInput(t);
        Result res = pass.getNext(t);
        assertEquals(t, res.result);
        fail.attachInput(t);
        res = fail.getNext(t);
        assertEquals(res.returnStatus, POStatus.STATUS_EOP);

        while (true) {
            res = projFil.getNext(t);
            if (res.returnStatus == POStatus.STATUS_EOP)
                break;
            assertEquals(POStatus.STATUS_OK, res.returnStatus);
            Tuple output = (Tuple) res.result;
            assertEquals(true, TestHelper.bagContains(inp, output));
            assertEquals(true, (Integer) ((Tuple) res.result).get(1) > 50);
        }
    }

    @Test
    public void testSimpleFilter() throws Exception {
        // Build the inner expression
        POProject p1 = GenPhyOp.exprProject(0);
        POProject p2 = GenPhyOp.exprProject(1);
        GreaterThanExpr gt = GenPhyOp.compGreaterThanExpr(p1, p2, DataType.INTEGER);

        PhysicalPlan ip = new PhysicalPlan();
        ip.add(p1);
        ip.add(p2);
        ip.add(gt);
        ip.connect(p1, gt);
        ip.connect(p2, gt);

        int[] ints = {0, 1, 1, 0, 1, 1};
        TupleFactory tf = TupleFactory.getInstance();
        DataBag inbag = BagFactory.getInstance().newDefaultBag();
        for (int i = 0; i < ints.length; i+=2) {
            Tuple t = tf.newTuple(2);
            t.set(0, new Integer(ints[i]));
            t.set(1, new Integer(ints[i+1]));
            inbag.add(t);
        }

        PORead read = GenPhyOp.topReadOp(inbag);
        POFilter filter = GenPhyOp.connectedFilterOp(read);
        filter.setPlan(ip);

        PhysicalPlan op = new PhysicalPlan();
        op.add(filter);
        op.add(read);
        op.connect(read, filter);

        DataBag outbag = BagFactory.getInstance().newDefaultBag();
        Result res;
        Tuple t = tf.newTuple();
        do {
            res = filter.getNext(t);
            if (res.returnStatus == POStatus.STATUS_OK) {
                outbag.add((Tuple)res.result);
            }
        } while (res.returnStatus == POStatus.STATUS_OK);
        assertEquals(POStatus.STATUS_EOP, res.returnStatus);
        assertEquals(1, outbag.size());
        Iterator<Tuple> i = outbag.iterator();
        assertTrue(i.hasNext());
        t = i.next();
        assertEquals(2, t.size());
        assertTrue(t.get(0) instanceof Integer);
        assertTrue(t.get(1) instanceof Integer);
        Integer i1 = (Integer)t.get(0);
        Integer i2 = (Integer)t.get(1);
        assertEquals(1, (int)i1);
        assertEquals(0, (int)i2);
    }

    @Test
    public void testAndFilter() throws Exception {
        // Build the inner expression
        POProject p1 = GenPhyOp.exprProject(0);
        ConstantExpression c2 = GenPhyOp.exprConst();
        c2.setValue(new Integer(0));
        GreaterThanExpr gt = GenPhyOp.compGreaterThanExpr(p1, c2, DataType.INTEGER);

        POProject p3 = GenPhyOp.exprProject(1);
        ConstantExpression c = GenPhyOp.exprConst();
        c.setValue(new Integer(1));
        EqualToExpr eq = GenPhyOp.compEqualToExpr(p3, c, DataType.INTEGER);
        POAnd and = GenPhyOp.compAndExpr(gt, eq);

        PhysicalPlan ip = new PhysicalPlan();
        ip.add(p1);
        ip.add(c2);
        ip.add(gt);
        ip.add(p3);
        ip.add(c);
        ip.add(eq);
        ip.add(and);
        ip.connect(p1, gt);
        ip.connect(c2, gt);
        ip.connect(p3, eq);
        ip.connect(c, eq);
        ip.connect(eq, and);
        ip.connect(gt, and);

        int[] ints = {0, 1, 1, 0, 1, 1};
        TupleFactory tf = TupleFactory.getInstance();
        DataBag inbag = BagFactory.getInstance().newDefaultBag();
        for (int i = 0; i < ints.length; i+=2) {
            Tuple t = tf.newTuple(2);
            t.set(0, new Integer(ints[i]));
            t.set(1, new Integer(ints[i+1]));
            inbag.add(t);
        }

        PORead read = GenPhyOp.topReadOp(inbag);
        POFilter filter = GenPhyOp.connectedFilterOp(read);
        filter.setPlan(ip);

        PhysicalPlan op = new PhysicalPlan();
        op.add(filter);
        op.add(read);
        op.connect(read, filter);

        DataBag outbag = BagFactory.getInstance().newDefaultBag();
        Result res;
        Tuple t = tf.newTuple();
        do {
            res = filter.getNext(t);
            if (res.returnStatus == POStatus.STATUS_OK) {
                outbag.add((Tuple)res.result);
            }
        } while (res.returnStatus == POStatus.STATUS_OK);
        assertEquals(POStatus.STATUS_EOP, res.returnStatus);
        assertEquals(1, outbag.size());
        Iterator<Tuple> i = outbag.iterator();
        assertTrue(i.hasNext());
        t = i.next();
        assertEquals(2, t.size());
        assertTrue(t.get(0) instanceof Integer);
        assertTrue(t.get(1) instanceof Integer);
        Integer i1 = (Integer)t.get(0);
        Integer i2 = (Integer)t.get(1);
        assertEquals(1, (int)i1);
        assertEquals(1, (int)i2);
    }

    public static void main(String[] args) {
        TestFilter tf = new TestFilter();
        try {
            tf.testAndFilter();
        } catch (Exception e) {
            System.out.println("Caught exception: " + e.getMessage());
        }
    }
}
