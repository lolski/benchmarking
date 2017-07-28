#!groovy
//This sets properties in the Jenkins server. In this case run every 8 hours
properties([pipelineTriggers([cron('H H/8 * * *')])])

//Function to run all the tests.
// @param buildBranch is the branch on the repo to run against
def buildOnBranch = { String buildBranch ->
		def workspace = pwd()
		//Everything is wrapped in a try catch so we can handle any test failures
		//If one test fails then all the others will stop. I.e. we fail fast
		try {
			//Always wrap each test block in a timeout
			//This first block sets up engine within 15 minutes
			timeout(15) {
				dir('grakn') {
					git url: 'https://github.com/graknlabs/grakn', branch: buildBranch
					stage(buildBranch+' Build Grakn') { //Stages allow you to organise and group things within Jenkins
						sh 'npm config set registry http://registry.npmjs.org/'
						sh 'if [ -d ' + workspace + '/maven ] ;  then rm -rf ' + workspace + '/maven ; fi'
						sh 'mvn versions:set "-DnewVersion=stable" "-DgenerateBackupPoms=false"'
						sh 'mvn clean install -Dmaven.repo.local=' + workspace + '/maven -DskipTests -B -U -Djetty.log.level=WARNING -Djetty.log.appender=STDOUT'
					}
					stage(buildBranch+' Init Grakn') {
						sh 'if [ -d grakn-package ] ; then grakn-package/bin/grakn.sh stop ; fi'
						sh 'if [ -d grakn-package ] ;  then rm -rf grakn-package ; fi'
						sh 'mkdir grakn-package'
						sh 'tar -xf grakn-dist/target/grakn-dist*.tar.gz --strip=1 -C grakn-package'
						//todo: remove the timeout hack when problem resolved
						sh 'sed -i.bak \'s/30/120/\' grakn-package/bin/grakn-engine.sh'
						sh 'grakn-package/bin/grakn.sh start'
					}
					stage(buildBranch+' Test Connection') {
						sh 'grakn-package/bin/graql.sh -e "match \\\$x;"' //Sanity sheck query. I.e. is everything working?
					}
				}
				dir('ldbc-driver') {
					git url: 'https://github.com/ldbc/ldbc_driver', branch: 'master'
					stage(buildBranch+' Build LDBC Driver') {
						sh 'mvn -U clean install -DskipTests -Dmaven.repo.local=' + workspace + '/maven '
					}
				}
			}
      //The actual tests
			dir('benchmarking') {
				checkout scm //Checkout the repo this jenkins files comes from

				timeout(30) {
					dir('single-machine-graph-scaling') {
					    stage(buildBranch+' Scale Test') {
					        sh 'mvn clean -U package -Dmaven.repo.local=' + workspace + '/maven ' //Point to local repo. Not the one on the machine
					        sh 'java -jar target/single-machine-graph-scaling-stable-allinone.jar'
					    }
					}
				}

				dir('snb-interactive-grakn') {
					stage(buildBranch+' Build LDBC Connector') {
						sh 'mvn -U clean install assembly:single -Dmaven.repo.local=' + workspace + '/maven -DskipTests'
					}
				}

        //Sets up environmental variables which can be shared between multiple tests
				withEnv(['VALIDATION_DATA=/home/jenkins/readwrite_neo4j--validation_set.tar.gz',
						'CSV_DATA=' + workspace + 'benchmarking/generate-SNB/social_network',
						'KEYSPACE=snb',
						'ENGINE=localhost:4567',
						'ACTIVE_TASKS=1000',
						'PATH+EXTRA=' + workspace + '/grakn/grakn-package/bin',
						'LDBC_DRIVER=' + workspace + '/ldbc-driver/target/jeeves-0.3-SNAPSHOT.jar',
						'LDBC_CONNECTOR=' + workspace + '/benchmarking/snb-interactive-grakn/target/snb-interactive-grakn-stable-jar-with-dependencies.jar',
						'LDBC_VALIDATION_CONFIG=readwrite_grakn--ldbc_driver_config--db_validation.properties']) {
					timeout(90) {
						dir('generate-SNB') {
							stage(buildBranch+' Load Validation Data') {
								sh './load-SNB.sh arch validate'
							}
						}
						stage(buildBranch+' Measure Size') {
							sh '../grakn/grakn-package/bin/nodetool flush'
								sh 'du -hd 0 ../grakn/grakn-package/db/cassandra/data'
						}
					}
					timeout(360) {
						dir('validate-SNB') {
							stage(buildBranch+' Validate Graph') {
								sh './validate.sh'
							}
						}
					}
				}
			}
			slackSend channel: "#github", message: "Periodic Build Success on "+buildBranch+": ${env.BUILD_NUMBER} (<${env.BUILD_URL}flowGraphTable/|Open>)"
		} catch (error) {
			slackSend channel: "#github", message: "Periodic Build Failed on "+buildBranch+": ${env.BUILD_NUMBER} (<${env.BUILD_URL}flowGraphTable/|Open>)"
				throw error
		} finally { // Tears down test environment
			timeout(5) {
				withEnv(['ENGINE=localhost:4567']) {
					dir('benchmarking/tools') {
						sh './check-errors.sh' //Uses rest API to look for any failed jobs and then gets the stack trace of the failed jobs
					}
				}

				dir('grakn') {
					stage(buildBranch+' Tear Down Grakn') {
						sh 'if [ -d ' + workspace + '/maven ] ;  then rm -rf ' + workspace + '/maven ; fi'
						sh 'cp grakn-package/logs/grakn.log '+buildBranch+'.log'
						archiveArtifacts artifacts: buildBranch+'.log'
						sh 'grakn-package/bin/grakn.sh stop'
						sh 'if [ -d grakn-package ] ;  then rm -rf grakn-package ; fi'
					}
				}
			}
		}
}

//Key - Value dictionary of jobs to run
//For example for the first one "master" is the key it is saying to run a job on node('slave3') and that job is buildOnBranch(masterBranch)
//For each entry in the dictionary a parallel job is run. Except for the last entry which is magic used to fail fast. In this case it allows each branch to run even if one fails.
def jobs = ['master':{node('slave2'){masterBranch = 'master'; buildOnBranch(masterBranch)}}, 'stable':{node('slave2'){stableBranch = 'stable'; buildOnBranch(stableBranch)}}, failFast: false]
parallel jobs
