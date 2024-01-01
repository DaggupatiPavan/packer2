# main.tf
#test
provider "aws" {
  region = "us-east-1" # e.g., us-east-1
}

# Assuming you already have an existing VPC with ID "vpc-xxxxxxxxxxxxxxxxx"
data "aws_vpc" "existing_vpc" {
  id = "vpc-05d7fb7f1331f8f16"
  
}

# Assuming you already have an existing security group within the VPC
# data "aws_security_group" "existing_security_group" {
#   vpc_id = data.aws_vpc.existing_vpc.id

#   filter {
#     name   = "group-name"
#     values = ["nextgen-devops-sg"]
#   }

#   filter {
#     name   = "tag:Group"
#     values = ["nextgen-devops-sg"]
#   }
# }

resource "aws_security_group" "myvpc1-sg" {
  name="myvpc1-sg"

  vpc_id=data.aws_vpc.existing_vpc.id

  ingress {

    from_port=22
    to_port=22
    protocol="tcp"
    cidr_blocks=[data.aws_vpc.existing_vpc.cidr_block]
    ipv6_cidr_blocks = ["::/0"]


  }

  egress {
    from_port = 0
    to_port=0
    protocol="-1"
    cidr_blocks=["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  tags = {
    Name="allow_tls"
  }

}


# Assuming you already have an existing private subnet within the VPC
data "aws_subnet" "existing_private_subnet" {
  vpc_id                  = data.aws_vpc.existing_vpc.id
  # Specify criteria to uniquely identify the private subnet
  id= "subnet-02cf2e19298b8cdac"
  cidr_block              = "10.63.20.0/25"
}

variable "instance_count" {
  default = 2
}

resource "aws_instance" "example_instance" {
 count = var.instance_count
  ami           = "ami-0c7217cdde317cfec" # Replace with the desired AMI ID
  instance_type = "c6i.large"    # Adjust the instance type as needed
  subnet_id     = data.aws_subnet.existing_private_subnet.id
  key_name      = "nextgen-devops-team"

#   security_group_name = [data.aws_security_group.existing_security_group.name]

  tags = {
    Name = "${count.index + 1}"  # Adds a unique index to each instance name
    Role = count.index == 0 ? "postgres" : "hammer"  # Assigns different roles to each instance
  }
}
output "private_ips" {
  value = aws_instance.example_instance[*].private_ip
}
