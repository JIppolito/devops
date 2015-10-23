Amazon Cleanup Scripts
======================

Find orphaned and duplicate AWS resources

### Usage

Install dependencies:

    bundle install

Run the script with the following supplied arguments:

* `-a` AWS Access Key [Required]

* `-s` AWS Secret Key [Required]

* `-r` AWS Region [Optional] default: us-east-1

Example:

    ruby bin/run.rb -a (AWS_access_key) -s (AWS_secret_key)