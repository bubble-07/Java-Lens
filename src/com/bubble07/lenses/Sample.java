package com.bubble07.lenses;

import java.util.ArrayList;
import java.util.Optional;

/**
 * A simple sample of some lens functionality to update multiple fields.
 * This sets up classes that could (if you squint really hard) be used in a game of Pong,
 * but you probably don't want to, because this is horribly contrived. 
 * @author bubble-07
 *
 */
public class Sample  {
	
	public static class Line  {
		//A bit annoying, but to use the lens functionality in the most natural way,
		//it's easiest to declare Field data members and then define accessor functions
		//with the same name. This is bad, but not as bad as declaring setters and getters manually!
		//(because you get them for free by, e.g: pt1().get() or pt1().set())
		//If special functionality is needed for setters/getters, just define them as is usual in Java,
		//and note that nothing in Lens requires a Field over a FieldReference -- just return a custom FieldReference!
		private Field<Point> pt1 = Field.withDefault(new Point());
		private Field<Point> pt2 = Field.withDefault(new Point());
		public Field<Point> pt1() { return pt1; }
		public Field<Point> pt2() { return pt2; }
		public void shiftY(Float diff) {
			Lens.bothOf(Line::pt1, Line::pt2).perform((p) -> p.shiftY(diff));
		}
	}
	
	public static class Vector  {
		private Field<Float> x = Field.withDefault(0f);
		private Field<Float> y = Field.withDefault(0f);
		public Field<Float> x() { return x; }
		public Field<Float> y() { return y; }
	}
	
	public static class Point extends Vector {
		public void shiftY(Float diff) {
			y().set(y().get() + diff);
		}
	}
	
	public static class Circle  {
		private Field<Point> center = Field.withDefault(new Point());
		private Field<Float> radius = Field.withDefault(0f);
		public Field<Point> center() { return center; }
		public Field<Float> radius() { return radius; }
	}
	private Field<Circle> ball = Field.withDefault(new Circle());
	public Field<Circle> ball() { return ball; }
	
	//Set player1's x position to -100f, y position 0f, with a height of 20f.
	//This one does it by setting the y positions of points separately, and the x positions of points combined
	private Field<Line> player1 = Field.withDefault(
			Common.chain(Lens.of(Line::pt1).focus(Point::y).set(10f),
				     Lens.of(Line::pt2).focus(Point::y).set(-10f),
				     Lens.bothOf(Line::pt1, Line::pt2).focus(Point::x).set(-100f))
				   .apply(new Line()));
	public Field<Line> player1() { return player1; }
	
	//For player2, do the same, but at an x position of 100f
	//This one does it by manually setting each point's position.
	private Field<Line> player2 = Field.withDefault(
			Common.chain(Lens.of(Line::pt1).focus(Lens.split(Point::x, Point::y)).set(Common.Pair.of(-100f, 10f)),
					     Lens.of(Line::pt2).focus(Lens.split(Point::x, Point::y)).set(Common.Pair.of(-100f, -10f)))
				   .apply(new Line()));
	public Field<Line> player2() { return player2; }
	
	public void movePlayerOne(Float ydiff) {
		Lens.of(Sample::player1).perform((l) -> l.shiftY(ydiff));
	}
	public void movePlayerTwo(Float ydiff) {
		player2().get().shiftY(ydiff);
	}
	
	//You wouldn't do this in Pong, and you wouldn't do this in code,
	//but move both players by explicitly traversing
	//the nested structure of Line/Point instances to update y positions.
	public void moveBothPlayers(Float ydiff) {
		Lens.bothOf(Sample::player1, Sample::player2)
		    .focus(Lens.bothOf(Line::pt1, Line::pt2)
		    .focus(Point::y)).apply((y) -> y + ydiff)
		.apply(this);
	}
	
	//Assuming that someone wrote the above function, this would be the natural refactor
	//after implementing a "shiftY" method on lines. 
	public void moveBothPlayersRefactored(Float ydiff) {
		Lens.bothOf(Sample::player1, Sample::player2)
		    .perform((l) -> l.shiftY(ydiff));
	}
	
	//Oh wait, Pong doesn't have 3 players either. Ah well, I'm sure nobody will notice.
	private Field<Line> player3 = Field.withDefault(new Line());
	public Field<Line> player3() { return player3; }
	
	
	//In the same style as "moveBothPlayers", update the y position of all 3 players in the
	//painstakingly tedious way.
	public void moveThreePlayers(Float ydiff) {
		Lens.bothOf(Sample::player1, Lens.bothOf(Sample::player2, Sample::player3))
	    .focus(Lens.bothOf(Line::pt1, Line::pt2)
	    .focus(Point::y)).apply((y) -> y + ydiff)
	    .apply(this);
	}
	
	//In the same style as "moveBothPlayersRefactored", update the y position of all 3 players.
	//Demonstrates (in a limited way) the ease of refactoring code that updates structures
	//using lenses -- lens operations on some deeply-nested structure can be lifted to dedicated
	//functions veratabim, and the enclosing context doesn't need to change. 
	public void moveThreePlayersRefactored(Float ydiff) {
		Lens.bothOf(Sample::player1, Lens.bothOf(Sample::player2, Sample::player3))
		     .perform((l) -> l.shiftY(ydiff));
	}

	
	public void main() {
		//Whenever the kind of thing you're lensing over is a generic type whose type parameter is initially unknown,
		//you may need to use the static methods in the Lens interface rather than the instance methods. 
		//This is quite simply because the limited inference of generic types in Java __sucks__
		Lens.apply(Lens.focus(Lens.listElements(), Lens.flatMapOptional()), (Integer x) -> Optional.of(x)).apply(new ArrayList<Optional<Integer>>());
	}
}
