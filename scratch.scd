z.synth[\filter][0].set(\buf, z.shapeBufferList[6]);
z.synth[\filter][1].set(\buf, z.shapeBufferList[0]);

z.synth[\filter][0].set(\lpfRatio, 9);

z.shapeBufferList.do({ arg buf; buf.plot });

z.bus[\tap][0].scope;


{ FreqScope.new(400, 200, z.bus[\tap][0].index, server: s); }.defer;