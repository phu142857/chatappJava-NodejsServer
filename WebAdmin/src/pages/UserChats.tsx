import { useEffect, useState } from 'react';
import { App as AntdApp, Avatar, Card, List, Typography, Tag, Space, Button } from 'antd';
import { MessageOutlined, UserOutlined, TeamOutlined } from '@ant-design/icons';
import apiClient from '../api/client';
import dayjs from 'dayjs';

const { Title, Text } = Typography;

interface Chat {
  _id: string;
  type: 'private' | 'group';
  name?: string;
  participants: Array<{
    _id: string;
    username: string;
    avatar?: string;
  }>;
  lastMessage?: {
    content: string;
    sender: {
      username: string;
    };
    timestamp: string;
  };
  createdAt: string;
  updatedAt: string;
}

export default function UserChats() {
  const { message: messageApi } = AntdApp.useApp();
  const [loading, setLoading] = useState(false);
  const [chats, setChats] = useState<Chat[]>([]);

  useEffect(() => {
    fetchChats();
  }, []);

  const fetchChats = async () => {
    try {
      setLoading(true);
      const response = await apiClient.get('/chats');
      setChats(response.data.data.chats || []);
    } catch (error) {
      console.error('Failed to fetch chats:', error);
      messageApi.error('Failed to load chats');
    } finally {
      setLoading(false);
    }
  };

  const getChatDisplayName = (chat: Chat) => {
    if (chat.type === 'group') {
      return chat.name || 'Group Chat';
    } else {
      // For private chats, show the other participant's name
      const otherParticipant = chat.participants.find(p => p._id !== getCurrentUserId());
      return otherParticipant?.username || 'Unknown User';
    }
  };

  const getCurrentUserId = () => {
    const userStr = localStorage.getItem('user');
    if (!userStr) return null;
    try {
      const user = JSON.parse(userStr);
      return user._id;
    } catch {
      return null;
    }
  };

  const getChatIcon = (chat: Chat) => {
    if (chat.type === 'group') {
      return <TeamOutlined />;
    } else {
      const otherParticipant = chat.participants.find(p => p._id !== getCurrentUserId());
      return otherParticipant?.avatar ? 
        <Avatar size="small" src={otherParticipant.avatar} /> : 
        <UserOutlined />;
    }
  };

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto', padding: '24px' }}>
      <Title level={2}>My Chats</Title>
      
      <Card>
        <List
          loading={loading}
          dataSource={chats}
          renderItem={(chat) => (
            <List.Item
              key={chat._id}
              actions={[
                <Button type="link" size="small">
                  View Messages
                </Button>
              ]}
            >
              <List.Item.Meta
                avatar={getChatIcon(chat)}
                title={
                  <Space>
                    <Text strong>{getChatDisplayName(chat)}</Text>
                    <Tag color={chat.type === 'group' ? 'blue' : 'green'}>
                      {chat.type === 'group' ? 'Group' : 'Private'}
                    </Tag>
                  </Space>
                }
                description={
                  <div>
                    {chat.lastMessage ? (
                      <div>
                        <Text type="secondary">
                          {chat.lastMessage.sender.username}: {chat.lastMessage.content}
                        </Text>
                        <br />
                        <Text type="secondary" style={{ fontSize: '12px' }}>
                          {dayjs(chat.lastMessage.timestamp).format('MMM DD, YYYY HH:mm')}
                        </Text>
                      </div>
                    ) : (
                      <Text type="secondary">No messages yet</Text>
                    )}
                    <div style={{ marginTop: 4 }}>
                      <Text type="secondary" style={{ fontSize: '12px' }}>
                        Created: {dayjs(chat.createdAt).format('MMM DD, YYYY')}
                      </Text>
                    </div>
                  </div>
                }
              />
            </List.Item>
          )}
        />
        
        {chats.length === 0 && !loading && (
          <div style={{ textAlign: 'center', padding: '40px 0' }}>
            <MessageOutlined style={{ fontSize: 48, color: '#d9d9d9' }} />
            <div style={{ marginTop: 16 }}>
              <Text type="secondary">No chats found</Text>
            </div>
          </div>
        )}
      </Card>
    </div>
  );
}
