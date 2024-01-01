output "instance_private_ip" {
   value = aws_instance.example_instance[*].private_ip
}
output "instance_IDs" {
  value = aws_instance.example_instance[*].id
}
