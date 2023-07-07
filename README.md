[![Build Status](https://gitlab.cs.uni-duesseldorf.de/stups/prob/prob2_eventb_algorithm_dsl/badges/master/pipeline.svg)](https://gitlab.cs.uni-duesseldorf.de/stups/prob/prob2_eventb_algorithm_dsl/pipelines)

## An Algorithm Description Language for Event-B ##

Algorithms define a set of instructions that are executed sequentially.
In this work, we present an Algorithm Description Language which can be used to specify simple sequential
algorithms.
The algorithms written in our Algorithm Description Language can then
be translated into formal specifications written in the Event-B specification language.
The translation uses techniques which can be used to optimize the specification which is
generated.

More details can be found in this article:

* Joy Clark, Jens Bendisposto, Stefan Hallerstede, Dominik Hansen, Michael Leuschel: Generating Event-B Specifications from Algorithm Descriptions. ABZ 2016: 183-197. https://doi.org/10.1007/978-3-319-33600-8_11

Full details are in the Master's thesis of Joy Clark from 2016 entitled "An Algorithm Description Language
for Event-B".

The original version of this code can be found at: https://github.com/joyheron/eventb_gen