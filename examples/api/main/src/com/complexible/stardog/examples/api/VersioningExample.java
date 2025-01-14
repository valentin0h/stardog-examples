/*
 * Copyright (c) 2010-2015 Clark & Parsia, LLC. <http://www.clarkparsia.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.complexible.stardog.examples.api;

import static com.complexible.common.rdf.model.Values.literal;
import static com.complexible.common.rdf.model.Values.namespace;
import static com.complexible.common.rdf.model.Values.uri;

import com.complexible.common.rdf.model.Values;
import com.complexible.stardog.api.update.UpdateHandler;
import com.complexible.stardog.api.update.UpdateOperation;
import com.complexible.stardog.api.update.UpdateSequence;
import com.complexible.stardog.api.update.Updates;
import com.complexible.stardog.api.update.io.UpdateSPARQLWriter;
import com.google.common.collect.Sets;

import java.util.List;
import java.util.Set;

import org.openrdf.model.Namespace;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.vocabulary.DC;
import org.openrdf.model.vocabulary.FOAF;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.rio.RDFFormat;

import com.complexible.common.iterations.Iteration;
import com.complexible.common.protocols.server.Server;
import com.complexible.common.rdf.rio.RDFWriters;
import com.complexible.stardog.Contexts;
import com.complexible.stardog.Stardog;
import com.complexible.stardog.StardogException;
import com.complexible.stardog.api.ConnectionConfiguration;
import com.complexible.stardog.api.admin.AdminConnection;
import com.complexible.stardog.api.admin.AdminConnectionConfiguration;
import com.complexible.stardog.api.versioning.Version;
import com.complexible.stardog.api.versioning.VersioningConnection;
import com.complexible.stardog.db.DatabaseOptions;
import com.complexible.stardog.protocols.snarl.SNARLProtocolConstants;
import com.complexible.stardog.versioning.VersioningOptions;
import com.google.common.collect.Lists;
import org.openrdf.rio.RDFHandlerException;

/**
 * <p>Simple example for versioning</p>
 *
 * @author  Evren Sirin
 * @since   2.2
 * @version 2.2
 */
public class VersioningExample {
	private static final String NS = "http://example.org/test/";
	private static final URI Alice = uri(NS, "Alice");
	private static final URI Bob = uri(NS, "Bob");
	private static final URI Charlie = uri(NS, "Charlie");

	// Versioning of RDF graphs
	// ---
	// New in Stardog 2.2 is the ability to [version RDF graphs](http://docs.stardog.com/using/#sd-Versioning).  This
	// gives you VCS-like commands and concepts, such as tags and revert, for your RDF graphs.
	public static void main(String[] args) throws Exception {
		// As always, we need to create and start a Stardog server for our example
		Server aServer = Stardog
			                 .buildServer()
			                 .bind(SNARLProtocolConstants.EMBEDDED_ADDRESS)
			                 .start();

		try {
			String aDB = "versionedDB";

			// Create an `AdminConnection` to Stardog to set up the database for the example
			AdminConnection dbms = AdminConnectionConfiguration.toEmbeddedServer()
			                                                   .credentials("admin", "admin")
			                                                   .connect();

			// If the database exists, drop and it create it fresh
			ConnectionConfiguration aConfig;
			try {
				if (dbms.list().contains(aDB)) {
					dbms.drop(aDB);
				}

				aConfig = dbms.disk(aDB)
				              .set(VersioningOptions.ENABLED, true)
				              .set(DatabaseOptions.NAMESPACES, Lists.newArrayList(namespace("", NS),
				                                                                  namespace("foaf", FOAF.NAMESPACE),
				                                                                  namespace("dc", DC.NAMESPACE)))
				              .create();
			}
			finally {
				dbms.close();
			}


			// Obtain a `Connection` to the database and request a view of the connection as a
			// [VersioningConnection](http://docs.stardog.com/java/snarl/com/complexible/stardog/api/versioning/VersioningConnection.html)
			VersioningConnection aConn = aConfig.connect().as(VersioningConnection.class);

			try {
				// Now, let's make some changes to the databases
				aConn.begin();
				aConn.add()
				     .statement(Alice, DC.PUBLISHER, literal("Alice"))
				     .statement(Bob, DC.PUBLISHER, literal("Bob"))
				     .statement(Alice, RDF.TYPE, FOAF.PERSON, Alice)
				     .statement(Alice, FOAF.MBOX, literal("mailto:alice@example.org"), Alice)
				     .statement(Bob, RDF.TYPE, FOAF.PERSON, Bob)
				     .statement(Bob, FOAF.MBOX, literal("mailto:bob@example.org"), Bob);

				// And we'll commit our changes with a commit message
				aConn.commit("Adding Alice and Bob");


				// Let's change Alice's email
				aConn.begin();
				aConn.remove()
				     .statements(Alice, FOAF.MBOX, literal("mailto:alice@example.org"), Alice);
				aConn.add()
				     .statement(Alice, FOAF.MBOX, literal("mailto:alice@another.example.org"), Alice);
				aConn.commit("Changing Alice's email");

				// Print the contents of the database and verify they are correct
				RDFWriters.write(aConn.get().context(Contexts.ALL).iterator(), RDFFormat.TRIG, aConn.namespaces(), System.out);

				// We can still use the regular commit function from the `Connection` interface. This will also create a new
				// version along with its metadata but it will not have a commit message
				aConn.begin();
				aConn.add()
				     .statement(Charlie, DC.PUBLISHER, literal("Charlie"))
				     .statement(Charlie, RDF.TYPE, FOAF.PERSON, Charlie)
				     .statement(Charlie, FOAF.MBOX, literal("mailto:charlie@example.org"), Charlie);
				aConn.commit();

				RDFWriters.write(aConn.get().context(Contexts.ALL).iterator(), RDFFormat.TRIG, aConn.namespaces(), System.out);

				// Lets try an example with the basic versioning API to list all versions
				Iteration<Version, StardogException> resultIt = aConn.versions()
				                                                     .find()
				                                                     .oldestFirst()
				                                                     .iterator();

				try {
					System.out.println("\nVersions: ");
					while (resultIt.hasNext()) {
						Version aVersion = resultIt.next();

						System.out.println(aVersion);
					}
				}
				finally {
					// don't forget to close your iteration!
					resultIt.close();
				}

				// We're at a good point with our data, we think it's a 1.0 version, so let's tag it so we could
				// come back to this state if need be
				String aTag = "Release 1.0";

				// Get the head (current) revision, that's what we're going to tag.
				Version aHeadVersion = aConn.versions().getHead();
				aConn.tags().create(aHeadVersion, aTag);

				// Now you can see the effects of having created the tag
				System.out.println(aHeadVersion.getTags());

				System.out.println("Tagged " + aHeadVersion.getURI() + " " + aTag);

				// Let's show a quick example of how to print out the diffs between two versions as SPARQL Update queries
				// These are the versions we want to calculate the diff between
				Version aTo = aHeadVersion;
				Version aFrom = aHeadVersion.getRelativeVersion(-2);

				System.out.println();
				System.out.println("Print the diffs between HEAD-2 and HEAD:");

				try {
					// We'll write the diffs as SPARQL update queries
					UpdateHandler aWriter = new UpdateSPARQLWriter(System.out);
					aWriter.startRDF();

					Iterable<Namespace> aNamespaces = aConn.namespaces();
					for (Namespace aNamespace : aNamespaces) {
						aWriter.handleNamespace(aNamespace.getPrefix(), aNamespace.getName());
					}

					UpdateSequence aDiff;

					final Set<Statement> aAdditions = Sets.newHashSet();
					final Set<Statement> aRemovals = Sets.newHashSet();

					Version aVersion = aFrom;
					// Iterate over the versions, grabbing the diff, and coalescing the changes
					while (aVersion != null) {
						aDiff = aVersion.getDiff();
						for (UpdateOperation aOp : aDiff) {
							Set<Statement> aAddTarget = aOp.getType() == UpdateOperation.Type.ADD ? aAdditions : aRemovals;
							Set<Statement> aRemoveTarget = aOp.getType() == UpdateOperation.Type.REMOVE ? aAdditions : aRemovals;
							for (Statement aStmt : toStatements(aOp)) {
								aAddTarget.add(aStmt);
								aRemoveTarget.remove(aStmt);
							}
						}
						aVersion = aVersion.equals(aTo) ? null : aVersion.getNext();
					}

					// now that we have all the changes, create the diff and write it out
					aDiff = Updates.newSequence(Updates.add(aAdditions), Updates.remove(aRemovals));

					Updates.handle(aDiff, aWriter);

					aWriter.endRDF();
				}
				catch (RDFHandlerException e) {
					throw new StardogException(e);
				}

				System.out.println();

				// Finally, we can revert.  Let's undo our last to commits and print the current data in the database
				// so we can see that we're back to where we started.
				aConn.revert(aHeadVersion.getRelativeVersion(-2), aHeadVersion, "Undo last two commits");

				RDFWriters.write(aConn.get().context(Contexts.ALL).iterator(), RDFFormat.TRIG, aConn.namespaces(), System.out);
			}
			finally {
				// Always close your connections when you're done
				aConn.close();
			}
		}
		finally {
			// You MUST stop the server if you've started it!
			aServer.stop();
		}
	}

	private static Iterable<Statement> toStatements(UpdateOperation theOp) throws StardogException {
		if (theOp.getNamedGraphs().isEmpty()) {
			return theOp.getStatements();
		}
		else {
			List<Statement> aResult = Lists.newArrayList();
			for (Statement aStmt : theOp.getStatements()) {
				for (URI aGraph : theOp.getNamedGraphs()) {
					aResult.add(Values.statement(aStmt.getSubject(), aStmt.getPredicate(), aStmt.getObject(), aGraph));
				}
			}
			return aResult;
		}
	}
}
