pipeline {
    agent any
    parameters {
        choice(name: 'action', choices: 'apply\ndestroy', description: 'Choose the action you want')
    }
    stages {
        stage('Clone') {
            steps {
                git branch: 'main', url: 'https://github.com/AbhishekRaoV/Intel_IceLake.git'
            }
        }
        stage('Build infra') {
            steps {
                script {
                    if (params.action == 'destroy') {
                        sh "terraform destroy --auto-approve "
                    }
                    if (params.action == 'apply') {
                        sh '''
                            terraform init
                            terraform validate
                            terraform plan -out=tfplan
                            terraform apply tfplan -no-color
                            terraform output -json private_ips | jq -r '.[]'
                        '''
                        waitStatus()
                        postgres_ip = sh(script: "terraform output -json private_ips | jq -r '.[]' | head -1", returnStdout: true).trim()
                        hammer_ip = sh(script: "terraform output -json private_ips | jq -r '.[]' | tail -1", returnStdout: true).trim()
                        sh '''
                        echo "Postgres IP: ${postgres_ip}"
                        echo "Hammer IP: ${hammer_ip}"
                        '''
                    }
                }
            }
        }

        stage('Generate Inventory file') {
            steps {
                script {
                    sh 'chmod +x inventoryfile.sh'
                    sh 'bash ./inventoryfile.sh'
                }
            }
        }

        

        stage('Install ansible') {
            steps {
                script {
                    sh "ssh -o StrictHostKeyChecking=no ubuntu@${postgres_ip} -- 'sudo apt update && sudo apt install ansible -y'"
                    sh "ssh -o StrictHostKeyChecking=no ubuntu@${hammer_ip} -- 'sudo apt update && sudo apt install ansible -y'"

                }
            }
        }

        stage('Install Tools') {
            steps {
                script {
                    sh """
                        ansible-playbook -i myinventory postgres_install.yaml
                        ansible-playbook -i myinventory hammerdb_install.yaml
                        ansible-playbook -i myinventory prometheus_install.yaml
                        ansible-playbook -i myinventory postgres_exporter_install.yaml -e postgres_ip=${postgres_ip}
                        ansible-playbook -i myinventory grafana_install.yaml
                    """
                }
            }
        }
        stage('Configure') {
            steps {
                script {
                    sh """
                        ansible-playbook -i myinventory postgres_config.yaml -e postgres_ip=${postgres_ip} -e hammer_ip=${hammer_ip}
                        ansible-playbook -i myinventory hammer_config.yaml -e postgres_ip=${postgres_ip}
                        ansible-playbook -i myinventory postgres_backup.yaml 
                        ansible-playbook -i myinventory test_hammer.yaml -e postgres_ip=${postgres_ip}
                        ansible-playbook -i myinventory restore_db.yaml 
                    """
                }
            }
        }
    }
}

def waitStatus(){
  def instanceIds = sh(returnStdout: true, script: "terraform output -json instance_IDs | tr -d '[]\"' | tr ',' ' '").trim().split(' ')
  for (int i = 0; i < instanceIds.size(); i++) {
    def instanceId = instanceIds[i]
    while (true) {
      def status = sh(returnStdout: true, script: "aws ec2 describe-instances --instance-ids ${instanceId} --query 'Reservations[].Instances[].State.Name' --output text").trim()
      if (status != 'running') {
        print '.'
      } else {
        println "Instance ${instanceId} is ${status}"
        sleep 10
        break  
      }
      sleep 5
    }
  }
}
