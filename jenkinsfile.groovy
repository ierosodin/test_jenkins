pipeline {
    agent any
    parameters {
        string(name: 'customer', defaultValue: 'wisen_ai', description: '')
        string(name: 'machine', defaultValue: '0', description: '')
        string(name: 'uuid', defaultValue: '00000000-0000-0000-0000-000000000000', description: '')
    }
    environment {
        DATETIME = sh(script: '''printf $(TZ=Etc/UCT date '+%FT%T%:z')''', , returnStdout: true)
        GIT_ACCOUNT = credentials("easonlai")
    }
    stages {
        stage("Initialization") {
            steps {
                buildName "#${BUILD_NUMBER} ${customer}/${machine}/${uuid}/${env.DATETIME}"
            }
        }
        stage('Git clone repo') {
            steps {
                sh 'pwd && ls'
                step(
                    [$class: 'WsCleanup']
                )
                sh 'pwd && ls'
                checkout([
                    $class: 'GitSCM', 
                    branches: [[name: '*/master']], 
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
        stage('Save license stage') {
            steps {
                sh label: '', script: '''
                docker cp ${DATETIME//[:,+]/-}:IAI/${customer}/${machine}/${uuid}/${DATETIME} record
                docker rm ${DATETIME//[:,+]/-}
                cp -rf record IAI/${customer}/${machine}/${uuid}/${DATETIME}
                # git add IAI/${customer}/${machine}/${uuid}/${DATETIME}
                # git commit -m "Build from Jenkins: IAI/${customer}/${machine}/${uuid}/${DATETIME}"
                # git push https://${GIT_ACCOUNT_USR}:${GIT_ACCOUNT_PSW}@gitlab.wisen.ai/industryai/licensegenerator.git HEAD:master
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
