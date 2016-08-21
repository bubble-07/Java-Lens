# Java-Lens
A reinvention of Haskell's Lens type in Java

Lenses provide the ability to "focus" on a member of a particular structure, apply traversals, and modify multiple members of a structure under a common, unified framework. 

The implementation of Lenses here relates to Haskell's by instantiation of the lens type Functor f => (A -> f B) -> (S -> f T) with the identity functor, which was done since Java lacks higher-kinded polymorphism. In exchange, the notion of a "Lens" in this library has been expanded to incorporate more of the "kitchen-sink" functionality provided by Haskell's Lens library, and in-place mutating variants of lensing operations are provided to complement (or add to the evil of, depending on who you ask) Java's nature as an imperative language.
