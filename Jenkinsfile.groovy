OP_HOST = '34.209.219.139'
OP_PORT = 55326
UID = "OP-${env.BUILD_ID}"

NODE_VER = "node10.16.3"

def isBranch(def branchName) {
	return env.BRANCH_NAME == branchName
}

def isMasterBranch() {
	return isBranch("master")
}

node {
	try {
		if (isMasterBranch()) {
			stage('Init') {
				def scmVars = checkout scm
				nodejs(NODE_VER) {
					sh "npm install"
				}
			}

			stage('Build') {
				BUILD_ZIP_NAME = "${UID}.zip"
				BUILD_ZIP_PATH = "${env.WORKSPACE}/${BUILD_ZIP_NAME}"
				zip dir: env.WORKSPACE, glob: '', zipFile: BUILD_ZIP_PATH
			}

			stage('Deploy') {
				withCredentials([usernamePassword(credentialsId: 'op_ssh_root', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
					def remote = [:]
					remote.name = 'op'
					remote.host = OP_HOST
					remote.port = OP_PORT
					remote.user = USERNAME
					remote.password = PASSWORD
					remote.allowAnyHosts = true
					
					def root = "/opt"
					def tmpZipPath = "${root}/${BUILD_ZIP_NAME}"
					def tmpPath = "${root}/${UID}"
					def runDir = "${root}/sample_express"
					sshPut remote: remote, from: BUILD_ZIP_PATH, into: root
					sh "rm -rf ${BUILD_ZIP_PATH}"
					
					sshCommand remote: remote, sudo: true, command: "chown -R root:root ${tmpZipPath}"
					sshCommand remote: remote, sudo: true, command: "unzip ${tmpZipPath} -d ${tmpPath}"
					sshCommand remote: remote, sudo: true, command: "rm -rf ${runDir} ${tmpZipPath}"
					sshCommand remote: remote, sudo: true, command: "mv ${tmpPath} ${runDir}"
					sshCommand remote: remote, command: "cd ${runDir} && sudo su -c 'node ${runDir}/app.js'"
				}
			}
		}
	} catch (e) {
		throw e
	}
}