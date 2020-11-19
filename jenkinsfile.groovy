pipeline {
    agent any
    parameters {
        string(name: 'customer', defaultValue: 'wisen_ai', description: '')
        string(name: 'machine', defaultValue: '0', description: '')
        string(name: 'uuid', defaultValue: '00000000-0000-0000-0000-000000000000', description: '')
    }
    environment {
        DATETIME = sh(script: '''printf $(TZ=Etc/UCT date '+%FT%T%:z')''', , returnStdout: true)
    }
    stages {
        stage("Initialization") {
            steps {
                buildName "#${BUILD_NUMBER} ${customer}/${machine}/${uuid}/${env.DATETIME}"
            }
        }
        stage('Git clone repo') {
            steps {
                step(
                    [$class: 'WsCleanup']
                )
                checkout([
                    $class: 'GitSCM', 
                    branches: [[name: '*/FB/ierosodin/gen_by_docker']], 
                    doGenerateSubmoduleConfigurations: true, 
                    extensions: [[$class: 'SubmoduleOption',
                                  parentCredentials: true,
                                  recursiveSubmodules: true,
                                  trackingSubmodules: true]],
                    submoduleCfg: [],
                    userRemoteConfigs: [[credentialsId: 'easonlai', url: 'https://gitlab.wisen.ai/industryai/licensegenerator.git']]
                ])
            }
        }
        stage('Build license stage') {
            steps {
                sh label: '', script: '''
                bash deploy.sh -c ${customer} -m ${machine} -u ${uuid} -d ${DATETIME}
                docker run --name ${DATETIME//[:,+]/-} licensegenerator:latest
                '''
            }
        }
        stage('Copy license stage') {
            steps {
                sh label: '', script: '''
                docker cp ${DATETIME//[:,+]/-}:IAI/${customer}/${machine}/${uuid}/${DATETIME} record
                docker cp ${DATETIME//[:,+]/-}:IAI/${customer}/${machine}/${uuid}/config.py record/
                docker rm ${DATETIME//[:,+]/-}
                '''
            }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: '**/record/*', onlyIfSuccessful: true, fingerprint: true
        }
    }

}
