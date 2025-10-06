#!/bin/bash

# Simple Git Workflow Script
# Usage: ./push-to-branch.sh "your-feature-name" "commit message"

if [ $# -ne 2 ]; then
    echo "Usage: ./push-to-branch.sh <branch-name> <commit-message>"
    echo "Example: ./push-to-branch.sh feature/user-management 'Add user CRUD operations'"
    exit 1
fi

BRANCH_NAME=$1
COMMIT_MESSAGE=$2

echo "🚀 Starting simple Git workflow..."

# Make sure we're on master
echo "📍 Switching to master branch..."
git checkout master

# Pull latest changes
echo "⬇️ Pulling latest changes from master..."
git pull origin master

# Add all changes
echo "📝 Adding all changes..."
git add .

# Commit changes
echo "💾 Committing changes..."
git commit -m "$COMMIT_MESSAGE"

# Create and switch to new branch
echo "🌿 Creating new branch: $BRANCH_NAME"
git checkout -b "$BRANCH_NAME"

# Push to the new branch
echo "⬆️ Pushing to branch: $BRANCH_NAME"
git push -u origin "$BRANCH_NAME"

echo ""
echo "✅ Success! Your code has been pushed to branch: $BRANCH_NAME"
echo ""
echo "🔗 Next steps:"
echo "1. Go to: https://github.com/imshubhy/gms.git"
echo "2. You'll see a 'Compare & pull request' button"
echo "3. Click it to create a Pull Request"
echo "4. Review and merge when ready"
echo ""
echo "🔄 After merging, run: git checkout master && git pull origin master"
