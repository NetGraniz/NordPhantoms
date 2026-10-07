package com.nordfjell.nordphantoms;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class PhantomPositionsTest {
    @Test void snapshotMovementWorldChangesAndRetirementKeepSpatialCountsCorrect(){
        var index=new PhantomPositions();UUID world=UUID.randomUUID(),other=UUID.randomUUID(),id=UUID.randomUUID();
        index.update(id,new PhantomPositions.Position(world,-129,70,0));
        assertEquals(1,index.count(world,-128,70,0,2));assertEquals(0,index.count(world,0,70,0,2));
        index.update(id,new PhantomPositions.Position(world,130,70,0));
        assertEquals(0,index.count(world,-128,70,0,2));assertEquals(1,index.count(world,128,70,0,2));
        index.update(id,new PhantomPositions.Position(other,130,70,0));
        assertEquals(0,index.count(world,128,70,0,2));assertEquals(1,index.count(other,128,70,0,2));
        index.remove(id);assertEquals(0,index.count(other,128,70,0,2));
    }
    @Test void distantEntitiesDoNotAffectLocalCounts(){
        var index=new PhantomPositions();UUID world=UUID.randomUUID();
        for(int i=0;i<1000;i++)index.update(UUID.randomUUID(),new PhantomPositions.Position(world,10000+i*256,70,10000));
        UUID near=UUID.randomUUID();index.update(near,new PhantomPositions.Position(world,1,70,1));
        assertEquals(1,index.count(world,0,70,0,16));
        index.clear();assertEquals(0,index.count(world,0,70,0,16));
    }
}
