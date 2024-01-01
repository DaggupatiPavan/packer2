pipeline {
    agent any
    parameters{
        choice(name: 'action',choices: 'apply\ndestroy',description: 'Choose the action you want')
    }
    stages {
        stage('Clone') {
            steps {
                git branch: 'main', url: 'https://github.com/AbhishekRaoV/Intel_IceLake.git'
            }
        }
        stage('Build infra'){
            steps{
                script{
                  if(params.action == 'destroy'){
                    sh "ssh ubuntu@10.63.20.41 -- 'cd /home/ubuntu/intel_icelake && terraform destroy --auto-approve '"
                  }
                  if(params.action == 'apply'){
                    sh '''
                    echo pavan | sudo -S rsync -e "ssh -i /var/lib/jenkins/.ssh/nextgen-devops-team.pem" -av --exclude=".git" ../intel_icelake ubuntu@10.63.20.41:/home/ubuntu
                    ssh ubuntu@10.63.20.41 -- 'cd /home/ubuntu/intel_icelake && terraform init'
                    ssh ubuntu@10.63.20.41 -- 'cd /home/ubuntu/intel_icelake && terraform validate'
                    ssh ubuntu@10.63.20.41 -- 'cd /home/ubuntu/intel_icelake && terraform plan -out=tfplan'
                    ssh ubuntu@10.63.20.41 -- 'cd /home/ubuntu/intel_icelake && terraform apply tfplan '
                    ssh ubuntu@10.63.20.41 -- 'cd /home/ubuntu/intel_icelake && terraform output -json private_ips | jq -r '.[]''
                    '''
                    instance()
                    sh "echo pavan | sudo -S scp -i /var/lib/jenkins/.ssh/nextgen-devops-team.pem -r ubuntu@10.63.20.41:/home/ubuntu/myinventory /var/lib/jenkins/workspace/intel_icelake"
                    }
                }
            }
        }

        stage('Install ansible'){
            steps{
                script{
                    sh" ssh ubuntu@${postgres_ip} -- 'sudo apt install ansible'"
                    sh" ssh ubuntu@${hammer_ip} -- 'sudo apt install ansible'"  
                }
            }
        }
        stage('Install Tools'){
            steps{
                script{
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
        stage('Configure'){
            steps{
                script{
                    sh """
                        ansible-playbook -i myinventory postgres_config.yaml -e postgres_ip=${postgres_ip}, hammer_ip=${hammer_ip}
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

def instance() {
    // Create an Ansible inventory file
    sh "ssh ubuntu@10.63.20.41 -- 'cd /home/ubuntu/intel_icelake && rm -rf myinventory 2> /dev/null && touch myinventory'"
    
    // Master node
    // sh '''
    //     ssh ubuntu@10.63.20.41 'cd /home/ubuntu/intel_icelake && echo "[postgres]" >> myinventory'
    //     ssh ubuntu@10.63.20.41 -- 'cd /home/ubuntu/intel_icelake && echo -n "ansible_host=" >> myinventory'
    //     ssh ubuntu@10.63.20.41 --
    //         'cd /home/ubuntu/intel_icelake &&
    //         terraform output --no-color -json instance_private_ip > output.json &&
    //         cat output.json |
    //         tr -d '[]"' |
    //         tr ',' '\\n' |
    //         head -1 |
    //         sed """s/\\$/ ansible_user=ubuntu/""" >> myinventory'

    //     '''

    // def postgres_ip = sh(script: "ssh ubuntu@10.63.20.41 -- 'cd /home/ubuntu/intel_icelake && cat output.json | tr -d \"[]\"' | tr ',' '\\n' | head -1 | sed 's/\\$/ ansible_user=ubuntu/'")

    // Worker nodes with ansible_user=ubuntu
    // sh '''
    //     ssh ubuntu@10.63.20.41 'cd /home/ubuntu/intel_icelake && echo "[hammer]" >> myinventory'
    //     ssh ubuntu@10.63.20.41 --
    //         'cd /home/ubuntu/intel_icelake &&
    //         terraform output -no-color -json instance_private_ip > output.json &&
    //         cat output.json |
    //         tr -d '[]"' |
    //         tr ',' '\\n' |
    //         tail -n +2 |
    //         sed """s/\\$/ ansible_user=ubuntu/""" >> myinventory'
    //     '''

    // def hammer_ip = sh(script: "ssh ubuntu@10.63.20.41 -- 'cd /home/ubuntu/intel_icelake && cat output.json | tr -d \"[]\"' | tr ',' '\\n' | tail -n +2 | sed 's/\\$/ ansible_user=ubuntu/'")
    // def workerIDs = sh(script: "ssh ubuntu@10.63.20.41 -- 'cd /home/ubuntu/intel_icelake && terraform output -json instance_IDs'", returnStdout: true).trim()
    
    // Copy the inventory file to the remote server
    sh "ssh ubuntu@10.63.20.41 -- 'cd /home/ubuntu/intel_icelake && echo ${workerIDs} >> clusterDetails'"
}
